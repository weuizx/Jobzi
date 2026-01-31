package dev.weuizx.jobzi.telegram

import dev.weuizx.jobzi.service.db.BroadcastDbService
import dev.weuizx.jobzi.service.db.BusinessUserDbService
import dev.weuizx.jobzi.service.db.UserDbService
import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.keyboard.KeyboardFactory
import dev.weuizx.jobzi.telegram.resolver.MessageResolver
import dev.weuizx.jobzi.telegram.state.ConversationState
import dev.weuizx.jobzi.telegram.state.ConversationStateManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

/**
 * Главный класс Telegram бота.
 * Отвечает только за извлечение данных из Telegram Update и передачу в MessageResolver.
 */
@Component
class JobziBot(
    private val botConfig: TelegramBotConfig,
    private val messageResolver: MessageResolver,
    private val userDbService: UserDbService,
    private val stateManager: ConversationStateManager,
    private val broadcastDbService: BroadcastDbService,
    private val businessUserDbService: BusinessUserDbService
) : TelegramLongPollingBot() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotToken(): String = botConfig.token

    override fun getBotUsername(): String = botConfig.username

    override fun onUpdateReceived(update: Update) {
        // Обрабатываем изменения статуса бота в чатах
        if (update.hasMyChatMember()) {
            handleMyChatMemberUpdate(update)
            return
        }

        // Проверяем, что это текстовое сообщение
        if (!update.hasMessage() || !update.message.hasText()) {
            return
        }

        val message = update.message
        val chatId = message.chatId
        val userId = message.from.id
        val text = message.text

        log.info("Received message from user $userId: $text")

        try {
            // Извлекаем данные из Telegram Update
            val incomingMessage = IncomingMessage(
                chatId = chatId,
                userId = userId,
                text = text,
                firstName = message.from.firstName,
                lastName = message.from.lastName,
                username = message.from.userName
            )

            // Передаем в resolver для обработки
            val response = messageResolver.resolve(incomingMessage)

            // Определяем, нужна ли клавиатура
            val keyboard = determineKeyboard(userId, text)

            // Отправляем ответ пользователю
            sendMessage(chatId, response, keyboard)

        } catch (e: Exception) {
            log.error("Error processing message from user $userId", e)
            sendMessage(chatId, "❌ Произошла ошибка при обработке команды")
        }
    }

    /**
     * Обрабатывает изменения статуса бота в чате (добавление/удаление)
     */
    private fun handleMyChatMemberUpdate(update: Update) {
        try {
            val myChatMember = update.myChatMember
            val chat = myChatMember.chat
            val newStatus = myChatMember.newChatMember.status
            val oldStatus = myChatMember.oldChatMember.status
            val userWhoChanged = myChatMember.from

            log.info("Bot chat member status changed in chat ${chat.id} (${chat.title}): $oldStatus -> $newStatus by user ${userWhoChanged.id}")

            // Проверяем, что бот был добавлен в чат
            val wasAdded = (oldStatus == "left" || oldStatus == "kicked") &&
                          (newStatus == "member" || newStatus == "administrator" || newStatus == "creator")

            if (!wasAdded) {
                log.info("Bot was not added to chat ${chat.id}, skipping auto-registration")
                return
            }

            // Получаем telegram_id пользователя, который добавил бота
            val telegramId = userWhoChanged.id

            // Находим пользователя в нашей базе по telegram_id
            val user = userDbService.findByTelegramId(telegramId)
            if (user == null) {
                log.info("User with telegram_id $telegramId who added bot to chat ${chat.id} is not registered in our system")
                return
            }

            // Проверяем, является ли пользователь представителем бизнеса (используем внутренний user.id)
            val businessUsers = businessUserDbService.findByUserId(user.id!!)

            if (businessUsers.isEmpty()) {
                log.info("User ${user.id} (telegram_id: $telegramId) who added bot to chat ${chat.id} is not a business representative")
                return
            }

            // Определяем тип чата
            val chatType = when (chat.type) {
                "group" -> "GROUP"
                "supergroup" -> "SUPERGROUP"
                "channel" -> "CHANNEL"
                else -> "PRIVATE"
            }

            // Регистрируем чат для всех бизнесов пользователя
            businessUsers.forEach { businessUser ->
                val channel = broadcastDbService.autoRegisterChat(
                    businessId = businessUser.businessId,
                    chatId = chat.id.toString(),
                    chatTitle = chat.title,
                    chatType = chatType
                )

                if (channel != null) {
                    log.info("Auto-registered chat ${chat.id} (${chat.title}) for business ${businessUser.businessId}")

                    // Отправляем уведомление пользователю
                    sendMessage(
                        chatId = telegramId,
                        text = "✅ Чат \"${chat.title ?: "без названия"}\" автоматически добавлен в список для рассылок"
                    )
                } else {
                    log.error("Failed to auto-register chat ${chat.id} for business ${businessUser.businessId}")
                }
            }

        } catch (e: Exception) {
            log.error("Error handling my_chat_member update", e)
        }
    }

    /**
     * Определяет, какую клавиатуру отправить пользователю
     */
    private fun determineKeyboard(userId: Long, messageText: String): ReplyKeyboard? {
        return when {
            // Суперадмин получает свою клавиатуру
            userDbService.isSuperAdmin(userId) -> determineSuperAdminKeyboard(userId, messageText)
            // Представители бизнеса получают свою клавиатуру
            userDbService.isBusinessRepresentative(userId) -> determineBusinessKeyboard(userId, messageText)
            // Соискатели получают свою клавиатуру
            else -> determineApplicantKeyboard(userId, messageText)
        }
    }

    /**
     * Определяет конкретную клавиатуру для суперадмина в зависимости от контекста
     */
    private fun determineSuperAdminKeyboard(userId: Long, messageText: String): ReplyKeyboard {
        val state = stateManager.getState(userId)

        return when (state) {
            ConversationState.NONE -> {
                // Главное меню или подменю
                when (messageText) {
                    "🔒 Управление доступом" -> KeyboardFactory.createAccessManagementMenu()
                    "◀️ Назад в меню", "❌ Отмена" -> KeyboardFactory.createSuperAdminMainMenu()
                    else -> KeyboardFactory.createSuperAdminMainMenu()
                }
            }
            ConversationState.SUPERADMIN_ACTIVATE_ENTER_TELEGRAM_ID,
            ConversationState.SUPERADMIN_ACTIVATE_ENTER_NAME,
            ConversationState.SUPERADMIN_BLOCK_ENTER_ID,
            ConversationState.SUPERADMIN_BLOCK_ENTER_REASON,
            ConversationState.SUPERADMIN_UNBLOCK_ENTER_ID -> {
                // При вводе данных - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.SUPERADMIN_ACTIVATE_ENTER_DESCRIPTION -> {
                // Можно пропустить описание
                KeyboardFactory.createSkipKeyboard()
            }
            else -> KeyboardFactory.createSuperAdminMainMenu()
        }
    }

    /**
     * Определяет клавиатуру для представителя бизнеса
     */
    private fun determineBusinessKeyboard(userId: Long, messageText: String): ReplyKeyboard {
        val state = stateManager.getState(userId)

        return when (state) {
            ConversationState.NONE -> {
                // Главное меню или возврат в меню
                when (messageText) {
                    "◀️ Назад в меню" -> KeyboardFactory.createBusinessMainMenu()
                    else -> KeyboardFactory.createBusinessMainMenu()
                }
            }
            ConversationState.VACANCY_CREATE_TITLE,
            ConversationState.VACANCY_CREATE_DESCRIPTION -> {
                // При вводе текста - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.VACANCY_CREATE_LOCATION,
            ConversationState.VACANCY_CREATE_SALARY -> {
                // Можно пропустить необязательные поля
                KeyboardFactory.createSkipKeyboard()
            }
            ConversationState.VACANCY_CREATE_PREVIEW -> {
                // Выбор: Опубликовать/Черновик/Отмена
                KeyboardFactory.createVacancyPreviewKeyboard()
            }
            ConversationState.VACANCY_CREATE_QUESTIONNAIRE_CHOICE -> {
                // Проверяем, добавляем ли мы еще вопросы или это начальный выбор
                val addingMoreQuestions = stateManager.getContextValue<Boolean>(userId, "addingMoreQuestions") ?: false
                if (addingMoreQuestions) {
                    KeyboardFactory.createAddAnotherQuestionKeyboard()
                } else {
                    KeyboardFactory.createQuestionnaireChoiceKeyboard()
                }
            }
            ConversationState.QUESTION_ADD_TEXT -> {
                // При вводе текста вопроса - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.QUESTION_ADD_TYPE -> {
                // Выбор типа вопроса (1-4)
                KeyboardFactory.createQuestionTypeKeyboard()
            }
            ConversationState.QUESTION_ADD_REQUIRED -> {
                // Выбор да/нет
                KeyboardFactory.createYesNoKeyboard()
            }
            ConversationState.QUESTION_ADD_OPTIONS -> {
                // При вводе вариантов - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.VIEWING_VACANCY_DETAILS -> {
                // Действия с вакансией: Редактировать/Анкета/Статус/Отклики/Назад
                KeyboardFactory.createVacancyActionsKeyboard()
            }
            ConversationState.VACANCY_EDIT_CHOOSE_FIELD -> {
                // Выбор поля для редактирования (1-4)
                KeyboardFactory.createVacancyEditFieldsKeyboard()
            }
            ConversationState.VACANCY_EDIT_INPUT_VALUE -> {
                // При вводе нового значения - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.VACANCY_CHANGE_STATUS -> {
                // Выбор статуса вакансии (1-4)
                KeyboardFactory.createVacancyStatusKeyboard()
            }
            ConversationState.VACANCY_DELETE_CONFIRM -> {
                // Подтверждение удаления вакансии
                KeyboardFactory.createDeleteConfirmKeyboard()
            }
            ConversationState.QUESTIONNAIRE_MANAGEMENT_MENU -> {
                // Управление анкетой: Добавить/Редактировать/Удалить/Заполнить заново/Назад
                KeyboardFactory.createQuestionnaireManagementKeyboard()
            }
            ConversationState.QUESTIONNAIRE_EDIT_CHOOSE_QUESTION,
            ConversationState.QUESTIONNAIRE_DELETE_ENTER_NUMBER -> {
                // При выборе номера вопроса - назад
                KeyboardFactory.createBackKeyboard()
            }
            ConversationState.QUESTIONNAIRE_EDIT_CHOOSE_FIELD -> {
                // Выбор поля вопроса для редактирования (1-2)
                KeyboardFactory.createQuestionEditFieldsKeyboard()
            }
            ConversationState.QUESTIONNAIRE_EDIT_INPUT_VALUE -> {
                // При вводе нового значения - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.VIEWING_APPLICATION_DETAILS -> {
                // Действия с откликом: Статус/Заметка/Назад
                KeyboardFactory.createApplicationActionsKeyboard()
            }
            ConversationState.VIEWING_VACANCY_APPLICATIONS -> {
                // Просмотр откликов вакансии - кнопки экспорта и назад
                KeyboardFactory.createVacancyApplicationsKeyboard()
            }
            ConversationState.CHANGING_APPLICATION_STATUS -> {
                // Выбор статуса (1-5)
                KeyboardFactory.createApplicationStatusKeyboard()
            }
            ConversationState.ADDING_APPLICATION_NOTES -> {
                // При вводе заметки - назад
                KeyboardFactory.createBackKeyboard()
            }
            // Broadcast states
            ConversationState.BROADCAST_MENU -> {
                // Главное меню рекламы
                KeyboardFactory.createBroadcastMenuKeyboard()
            }
            ConversationState.BROADCAST_CAMPAIGN_CREATE_TITLE,
            ConversationState.BROADCAST_CAMPAIGN_CREATE_MESSAGE -> {
                // При вводе текста - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.BROADCAST_CAMPAIGN_PREVIEW -> {
                // Предпросмотр кампании: Отправить/Редактировать/Отмена
                KeyboardFactory.createBroadcastPreviewKeyboard()
            }
            ConversationState.BROADCAST_CHANNEL_ADD_ID -> {
                // При вводе ID канала - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            ConversationState.BROADCAST_CHANNEL_MANAGEMENT -> {
                // Управление каналами
                KeyboardFactory.createChannelManagementKeyboard()
            }
            ConversationState.BROADCAST_CHANNEL_DELETE_CONFIRM -> {
                // Подтверждение удаления канала
                KeyboardFactory.createChannelDeleteConfirmKeyboard()
            }
            ConversationState.BROADCAST_CAMPAIGNS_LIST -> {
                // Просмотр списка кампаний - кнопка "Назад"
                KeyboardFactory.createBackKeyboard()
            }
            ConversationState.BROADCAST_CAMPAIGN_VIEW_DETAILS -> {
                // Детали кампании - действия
                KeyboardFactory.createCampaignDetailsKeyboard()
            }
            ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TYPE -> {
                // Выбор типа расписания (1-4)
                KeyboardFactory.createScheduleTypeKeyboard()
            }
            ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TIME -> {
                // Ввод времени - только отмена
                KeyboardFactory.createCancelKeyboard()
            }
            else -> KeyboardFactory.createBusinessMainMenu()
        }
    }

    /**
     * Определяет клавиатуру для соискателя
     */
    private fun determineApplicantKeyboard(userId: Long, messageText: String): ReplyKeyboard? {
        val state = stateManager.getState(userId)

        return when (state) {
            ConversationState.NONE -> {
                // Главное меню для соискателя
                KeyboardFactory.createApplicantMainMenu()
            }
            ConversationState.APPLICANT_CONFIRM_VACANCY -> {
                // Подтверждение отклика на вакансию
                KeyboardFactory.createYesNoKeyboard()
            }
            ConversationState.APPLICANT_ANSWERING_QUESTION -> {
                // При заполнении анкеты - проверяем, можно ли пропустить вопрос
                if (messageText.contains("пропустить", ignoreCase = true) ||
                    messageText.contains("отправив: -", ignoreCase = true) ||
                    messageText.contains("необязательный", ignoreCase = true)) {
                    KeyboardFactory.createSkipQuestionKeyboard()
                } else {
                    KeyboardFactory.createCancelKeyboard()
                }
            }
            else -> KeyboardFactory.createApplicantMainMenu()
        }
    }

    /**
     * Отправляет текстовое сообщение пользователю с опциональной клавиатурой
     */
    private fun sendMessage(chatId: Long, text: String, keyboard: ReplyKeyboard? = null) {
        try {
            val message = SendMessage().apply {
                this.chatId = chatId.toString()
                this.text = text
                keyboard?.let { this.replyMarkup = it }
            }
            execute(message)
        } catch (e: TelegramApiException) {
            log.error("Error sending message to chat $chatId", e)
        }
    }
}
