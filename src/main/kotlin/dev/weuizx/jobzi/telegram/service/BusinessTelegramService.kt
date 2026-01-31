package dev.weuizx.jobzi.telegram.service

import dev.weuizx.jobzi.domain.ApplicationStatus
import dev.weuizx.jobzi.domain.QuestionType
import dev.weuizx.jobzi.domain.VacancyStatus
import dev.weuizx.jobzi.service.BroadcastService
import dev.weuizx.jobzi.service.ExcelExportService
import dev.weuizx.jobzi.service.db.ApplicationDbService
import dev.weuizx.jobzi.service.db.BroadcastDbService
import dev.weuizx.jobzi.service.db.BusinessDbService
import dev.weuizx.jobzi.service.db.BusinessUserDbService
import dev.weuizx.jobzi.service.db.QuestionDbService
import dev.weuizx.jobzi.service.db.UserDbService
import dev.weuizx.jobzi.service.db.VacancyDbService
import dev.weuizx.jobzi.telegram.TelegramApiClient
import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.state.ConversationState
import dev.weuizx.jobzi.telegram.state.ConversationStateManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Сервис для обработки команд представителей бизнеса
 */
@Service
class BusinessTelegramService(
    private val userDbService: UserDbService,
    private val businessUserDbService: BusinessUserDbService,
    private val businessDbService: BusinessDbService,
    private val vacancyDbService: VacancyDbService,
    private val questionDbService: QuestionDbService,
    private val applicationDbService: ApplicationDbService,
    private val broadcastDbService: BroadcastDbService,
    private val broadcastService: BroadcastService,
    private val excelExportService: ExcelExportService,
    private val telegramApiClient: TelegramApiClient,
    private val stateManager: ConversationStateManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Форматирует текст сообщения, удаляя лишние отступы
     * Автоматически применяется ко всем строкам через перехватчик
     */
    private fun String.cleanMessage(): String {
        return this.trimIndent().lines().joinToString("\n") { it.trimStart() }
    }

    fun handleStart(message: IncomingMessage): String {
        // Получаем пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        // Получаем бизнесы пользователя
        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        // Берем первый бизнес (пока работаем с одним бизнесом на пользователя)
        val businessUser = businessUsers.first()
        val business = businessDbService.findById(businessUser.businessId)
            ?: return "❌ Бизнес не найден"

        // Проверяем активность бизнеса
        if (!business.isActive) {
            return """
                ⚠️ Ваш бизнес заблокирован

                Для получения информации свяжитесь с администратором.
            """.cleanMessage()
        }

        return buildMainMenuMessage(business.name, businessUser.role.name, businessUser.businessId)
    }

    fun handleCommand(message: IncomingMessage, command: String): String {
        // Сначала проверяем, находится ли пользователь в процессе создания вакансии
        val currentState = stateManager.getState(message.userId)
        if (currentState != ConversationState.NONE) {
            return handleConversationState(message)
        }

        // Обработка команд отмены
        if (command == "❌ Отмена") {
            stateManager.clearState(message.userId)
            return "❌ Действие отменено.\n\nВы вернулись в главное меню."
        }

        // Обработка обычных команд меню
        return when (command) {
            "📋 Мои вакансии" -> handleMyVacancies(message)
            "➕ Новая вакансия" -> handleNewVacancy(message)
            "👥 Все отклики" -> handleAllApplications(message)
            "📢 Реклама" -> handleBroadcastMenu(message)
            "📊 Статистика" -> handleStatistics(message)
            "⚙️ Настройки" -> handleSettings(message)
            "❓ Помощь" -> handleHelp(message)
            "◀️ Назад в меню" -> {
                stateManager.clearState(message.userId)
                handleStart(message)
            }
            else -> {
                // Проверяем, является ли команда кодом вакансии (ABC123)
                val vacancyCodePattern = Regex("[A-Z]{3}\\d{3}")
                if (vacancyCodePattern.matches(command.trim().uppercase())) {
                    handleViewVacancyByCode(message, command.trim().uppercase())
                } else {
                    // Проверяем, является ли команда числом (ID отклика)
                    val applicationId = command.trim().toLongOrNull()
                    if (applicationId != null) {
                        handleViewApplicationDetails(message, applicationId)
                    } else {
                        "❓ Неизвестная команда. Используйте меню для навигации."
                    }
                }
            }
        }
    }

    /**
     * Обработка состояний диалога
     */
    private fun handleConversationState(message: IncomingMessage): String {
        val state = stateManager.getState(message.userId)

        // Обработка отмены
        if (message.text == "❌ Отмена") {
            stateManager.clearState(message.userId)
            return "❌ Действие отменено.\n\nВы вернулись в главное меню."
        }

        return when (state) {
            ConversationState.VACANCY_CREATE_TITLE -> handleVacancyTitleInput(message)
            ConversationState.VACANCY_CREATE_DESCRIPTION -> handleVacancyDescriptionInput(message)
            ConversationState.VACANCY_CREATE_LOCATION -> handleVacancyLocationInput(message)
            ConversationState.VACANCY_CREATE_SALARY -> handleVacancySalaryInput(message)
            ConversationState.VACANCY_CREATE_PREVIEW -> handleVacancyPreviewAction(message)
            ConversationState.VACANCY_CREATE_QUESTIONNAIRE_CHOICE -> handleQuestionnaireChoice(message)
            ConversationState.QUESTION_ADD_TEXT -> handleQuestionTextInput(message)
            ConversationState.QUESTION_ADD_TYPE -> handleQuestionTypeInput(message)
            ConversationState.QUESTION_ADD_REQUIRED -> handleQuestionRequiredInput(message)
            ConversationState.QUESTION_ADD_OPTIONS -> handleQuestionOptionsInput(message)
            ConversationState.VIEWING_APPLICATION_DETAILS -> handleApplicationAction(message)
            ConversationState.VIEWING_VACANCY_APPLICATIONS -> handleVacancyApplicationInput(message)
            ConversationState.CHANGING_APPLICATION_STATUS -> handleStatusChange(message)
            ConversationState.ADDING_APPLICATION_NOTES -> handleNotesInput(message)
            ConversationState.VIEWING_VACANCY_DETAILS -> handleVacancyAction(message)
            ConversationState.VACANCY_EDIT_CHOOSE_FIELD -> handleVacancyEditChooseField(message)
            ConversationState.VACANCY_EDIT_INPUT_VALUE -> handleVacancyEditInputValue(message)
            ConversationState.VACANCY_CHANGE_STATUS -> handleVacancyChangeStatus(message)
            ConversationState.VACANCY_DELETE_CONFIRM -> handleVacancyDeleteConfirm(message)
            ConversationState.QUESTIONNAIRE_MANAGEMENT_MENU -> handleQuestionnaireManagementAction(message)
            ConversationState.QUESTIONNAIRE_DELETE_ENTER_NUMBER -> handleQuestionDeleteByNumber(message)
            ConversationState.QUESTIONNAIRE_EDIT_CHOOSE_QUESTION -> handleQuestionEditChooseQuestion(message)
            ConversationState.QUESTIONNAIRE_EDIT_CHOOSE_FIELD -> handleQuestionEditChooseField(message)
            ConversationState.QUESTIONNAIRE_EDIT_INPUT_VALUE -> handleQuestionEditInputValue(message)
            // Broadcast states
            ConversationState.BROADCAST_MENU -> handleBroadcastMenuAction(message)
            ConversationState.BROADCAST_CAMPAIGN_CREATE_TITLE -> handleCampaignTitleInput(message)
            ConversationState.BROADCAST_CAMPAIGN_CREATE_MESSAGE -> handleCampaignMessageInput(message)
            ConversationState.BROADCAST_CAMPAIGN_PREVIEW -> handleCampaignPreviewAction(message)
            ConversationState.BROADCAST_CHANNEL_ADD_ID -> handleChannelAddInput(message)
            ConversationState.BROADCAST_CHANNEL_MANAGEMENT -> handleChannelManagementAction(message)
            ConversationState.BROADCAST_CHANNEL_DELETE_CONFIRM -> handleChannelDeleteConfirm(message)
            ConversationState.BROADCAST_CAMPAIGNS_LIST -> handleCampaignSelectById(message)
            ConversationState.BROADCAST_CAMPAIGN_VIEW_DETAILS -> handleCampaignDetailsAction(message)
            ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TYPE -> handleCampaignScheduleTypeInput(message)
            ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TIME -> handleCampaignScheduleTimeInput(message)
            else -> {
                stateManager.clearState(message.userId)
                "❌ Произошла ошибка. Попробуйте снова."
            }
        }
    }

    private fun buildMainMenuMessage(businessName: String, role: String, businessId: Long): String {
        // Получаем статистику
        val vacancies = vacancyDbService.findByBusinessId(businessId)
        val activeVacancies = vacancies.count { it.status == VacancyStatus.ACTIVE }

        val allApplications = vacancies.flatMap { applicationDbService.findByVacancyId(it.id!!) }
        val newApplications = allApplications.count { it.status == ApplicationStatus.NEW }
        val totalCandidates = allApplications.size

        return """
            |🏢 $businessName
            |
            |📊 Текущее состояние:
            |• Активных вакансий: $activeVacancies
            |• Новых откликов: $newApplications
            |• Всего кандидатов: $totalCandidates
            |
            |────────────────────
            |
            |Выберите действие из меню ниже 👇
            |
            |📋 Мои вакансии - просмотр всех вакансий
            |➕ Новая вакансия - создать новую вакансию
            |👥 Все отклики - просмотреть отклики
            |📊 Статистика - аналитика бизнеса
            |⚙️ Настройки - настройки бизнеса
            |❓ Помощь - инструкция по использованию
        """.trimMargin()
    }

    private fun handleMyVacancies(message: IncomingMessage): String {
        // Получаем бизнес пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId

        // Получаем все вакансии бизнеса
        val vacancies = vacancyDbService.findByBusinessId(businessId)

        if (vacancies.isEmpty()) {
            return """
                📋 Мои вакансии

                У вас пока нет созданных вакансий.

                Нажмите "➕ Новая вакансия" для создания первой вакансии.
            """.cleanMessage()
        }

        // Группируем по статусам
        val active = vacancies.filter { it.status == VacancyStatus.ACTIVE }
        val draft = vacancies.filter { it.status == VacancyStatus.DRAFT }
        val paused = vacancies.filter { it.status == VacancyStatus.PAUSED }
        val closed = vacancies.filter { it.status == VacancyStatus.CLOSED }

        val result = buildString {
            appendLine("📋 Мои вакансии (${vacancies.size})")
            appendLine()
            appendLine("📊 Статистика:")
            appendLine("✅ Активных: ${active.size}")
            appendLine("📝 Черновиков: ${draft.size}")
            appendLine("⏸ Приостановленных: ${paused.size}")
            appendLine("🔒 Закрытых: ${closed.size}")
            appendLine()
            appendLine("────────────────────")
            appendLine()

            if (active.isNotEmpty()) {
                appendLine("✅ Активные вакансии:")
                appendLine()
                active.forEach { vacancy ->
                    val applicationsCount = applicationDbService.findByVacancyId(vacancy.id!!).size
                    val questionsCount = questionDbService.findByVacancyId(vacancy.id).size

                    appendLine("🆔 ${vacancy.code} - ${vacancy.title}")
                    appendLine("   📍 ${vacancy.location ?: "не указано"}")
                    appendLine("   💰 ${vacancy.salary ?: "не указана"}")
                    appendLine("   👥 Откликов: $applicationsCount")
                    appendLine("   📝 Вопросов: $questionsCount")
                    appendLine("   📅 Опубликована: ${vacancy.publishedAt?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "—"}")
                    appendLine()
                }
            }

            if (draft.isNotEmpty()) {
                appendLine("────────────────────")
                appendLine()
                appendLine("📝 Черновики:")
                appendLine()
                draft.forEach { vacancy ->
                    val questionsCount = questionDbService.findByVacancyId(vacancy.id!!).size

                    appendLine("🆔 ${vacancy.code} - ${vacancy.title}")
                    appendLine("   📍 ${vacancy.location ?: "не указано"}")
                    appendLine("   💰 ${vacancy.salary ?: "не указана"}")
                    appendLine("   📝 Вопросов: $questionsCount")
                    appendLine("   📅 Создана: ${vacancy.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
                    appendLine()
                }
            }

            if (paused.isNotEmpty()) {
                appendLine("────────────────────")
                appendLine()
                appendLine("⏸ Приостановленные:")
                appendLine()
                paused.forEach { vacancy ->
                    val applicationsCount = applicationDbService.findByVacancyId(vacancy.id!!).size

                    appendLine("🆔 ${vacancy.code} - ${vacancy.title}")
                    appendLine("   👥 Откликов: $applicationsCount")
                    appendLine()
                }
            }

            if (closed.isNotEmpty()) {
                appendLine("────────────────────")
                appendLine()
                appendLine("🔒 Закрытые:")
                appendLine()
                closed.take(5).forEach { vacancy ->
                    val applicationsCount = applicationDbService.findByVacancyId(vacancy.id!!).size

                    appendLine("🆔 ${vacancy.code} - ${vacancy.title}")
                    appendLine("   👥 Откликов: $applicationsCount")
                    appendLine()
                }
                if (closed.size > 5) {
                    appendLine("...и еще ${closed.size - 5} закрытых вакансий")
                    appendLine()
                }
            }

            appendLine("────────────────────")
            appendLine()
            appendLine("💡 Подсказка:")
            appendLine("Отправьте код вакансии (например: ABC123) для просмотра деталей")
        }

        return result
    }

    private fun handleNewVacancy(message: IncomingMessage): String {
        // Начинаем процесс создания вакансии
        stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_TITLE)

        return """
            ➕ Создание новой вакансии

            Шаг 1 из 4: Название вакансии

            Введите название вакансии (например: "Прораб на стройку" или "Разнорабочий"):
        """.cleanMessage()
    }

    private fun handleVacancyTitleInput(message: IncomingMessage): String {
        val title = message.text.trim()

        if (title.length < 5) {
            return "❌ Название слишком короткое. Минимум 5 символов.\n\nВведите название вакансии:"
        }

        if (title.length > 255) {
            return "❌ Название слишком длинное. Максимум 255 символов.\n\nВведите название вакансии:"
        }

        // Сохраняем название в контексте
        stateManager.setContextValue(message.userId, "title", title)

        // Переходим к следующему шагу
        stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_DESCRIPTION)

        return """
            ✅ Название сохранено: "$title"

            Шаг 2 из 4: Описание

            Опишите вакансию более подробно:
            • Что нужно делать
            • Какие требования к кандидату
            • Какие условия работы

            Введите описание:
        """.cleanMessage()
    }

    private fun handleVacancyDescriptionInput(message: IncomingMessage): String {
        val description = message.text.trim()

        if (description.length < 20) {
            return "❌ Описание слишком короткое. Минимум 20 символов.\n\nВведите описание вакансии:"
        }

        // Сохраняем описание
        stateManager.setContextValue(message.userId, "description", description)

        // Переходим к следующему шагу
        stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_LOCATION)

        return """
            ✅ Описание сохранено

            Шаг 3 из 4: Место работы

            Укажите адрес или район (например: "Москва, Южное Бутово" или "Подольск, центр").

            Если хотите пропустить, отправьте "-"
        """.cleanMessage()
    }

    private fun handleVacancyLocationInput(message: IncomingMessage): String {
        val location = message.text.trim()
        val locationValue = if (location == "-") null else location

        // Сохраняем место работы
        if (locationValue != null) {
            stateManager.setContextValue(message.userId, "location", locationValue)
        }

        // Переходим к следующему шагу
        stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_SALARY)

        return """
            ${if (locationValue != null) "✅ Место работы: $locationValue" else "⏭ Место работы пропущено"}

            Шаг 4 из 4: Зарплата

            Укажите зарплату (например: "50000 руб/мес" или "2000 руб/день" или "по договоренности").

            Если хотите пропустить, отправьте "-"
        """.cleanMessage()
    }

    private fun handleVacancySalaryInput(message: IncomingMessage): String {
        val salary = message.text.trim()
        val salaryValue = if (salary == "-") null else salary

        // Сохраняем зарплату
        if (salaryValue != null) {
            stateManager.setContextValue(message.userId, "salary", salaryValue)
        }

        // Инициализируем пустой список вопросов для накопления
        stateManager.setContextValue(message.userId, "pendingQuestions", emptyList<Map<String, Any>>())

        // Переходим сразу к настройке анкеты
        stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_QUESTIONNAIRE_CHOICE)

        return """
            ✅ Данные вакансии сохранены!

            ────────────────────

            📝 Теперь настройте анкету для соискателей

            По умолчанию будут добавлены 3 базовых вопроса:
            1. Как вас зовут? (текст, обязательный)
            2. Ваш номер телефона (телефон, обязательный)
            3. Сколько вам лет? (число, необязательный)

            Выберите действие:
            • Отправьте "Только базовые" - использовать только 3 базовых вопроса
            • Отправьте "Добавить свои" - добавить дополнительные вопросы
            • Отправьте "Пропустить" - завершить без анкеты (можно добавить позже)
        """.cleanMessage()
    }

    private fun handleVacancyPreviewAction(message: IncomingMessage): String {
        val action = message.text.trim().lowercase()

        return when {
            action.contains("опубликовать") -> handleVacancyPublish(message)
            action.contains("черновик") -> handleVacancySaveDraft(message)
            action.contains("отмена") -> {
                stateManager.clearState(message.userId)
                "❌ Создание вакансии отменено."
            }
            else -> """
                ❓ Неизвестное действие.

                Выберите:
                • "Опубликовать" - активировать вакансию
                • "Черновик" - сохранить как черновик
                • "Отмена" - отменить
            """.cleanMessage()
        }
    }

    private fun handleVacancyPublish(message: IncomingMessage): String {
        return saveVacancyWithQuestions(message, VacancyStatus.ACTIVE)
    }

    private fun handleVacancySaveDraft(message: IncomingMessage): String {
        return saveVacancyWithQuestions(message, VacancyStatus.DRAFT)
    }

    /**
     * Сохраняет вакансию и все накопленные вопросы из контекста
     */
    private fun saveVacancyWithQuestions(message: IncomingMessage, status: VacancyStatus): String {
        // Получаем business ID пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Ошибка: пользователь не найден"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ Ошибка: бизнес не найден"
        }

        val businessId = businessUsers.first().businessId

        // Получаем данные из контекста
        val title = stateManager.getContextValue<String>(message.userId, "title") ?: ""
        val description = stateManager.getContextValue<String>(message.userId, "description") ?: ""
        val location = stateManager.getContextValue<String>(message.userId, "location")
        val salary = stateManager.getContextValue<String>(message.userId, "salary")

        // Создаем вакансию
        val vacancy = vacancyDbService.createVacancy(
            businessId = businessId,
            title = title,
            description = description,
            location = location,
            salary = salary,
            status = status
        )

        // Получаем накопленные вопросы из контекста
        val pendingQuestions = stateManager.getContextValue<List<Map<String, Any>>>(message.userId, "pendingQuestions") ?: emptyList()

        // Сохраняем все вопросы в БД
        pendingQuestions.forEachIndexed { index, questionData ->
            val questionText = questionData["questionText"] as? String ?: ""
            val questionTypeName = questionData["questionType"] as? String ?: "TEXT"
            val questionType = QuestionType.valueOf(questionTypeName)
            val isRequired = questionData["isRequired"] as? Boolean ?: false

            @Suppress("UNCHECKED_CAST")
            val options = questionData["options"] as? List<String>

            questionDbService.createCustomQuestion(
                vacancyId = vacancy.id!!,
                questionText = questionText,
                questionType = questionType,
                isRequired = isRequired,
                options = options
            )
        }

        val questionsCount = pendingQuestions.size

        // Очищаем состояние
        stateManager.clearState(message.userId)

        return if (status == VacancyStatus.ACTIVE) {
            """
                ✅ Вакансия полностью настроена и опубликована!

                🆔 Код вакансии: ${vacancy.code}
                📋 Название: ${vacancy.title}
                📝 Вопросов в анкете: $questionsCount

                ────────────────────

                Соискатели могут откликнуться, отправив код: ${vacancy.code}

                Вы можете:
                • Посмотреть вакансию в разделе "📋 Мои вакансии"
                • Просматривать отклики в разделе "👥 Все отклики"

                Вакансия активна и доступна для откликов!
            """.cleanMessage()
        } else {
            """
                ✅ Вакансия полностью настроена и сохранена как черновик!

                🆔 Код вакансии: ${vacancy.code}
                📋 Название: ${vacancy.title}
                📝 Вопросов в анкете: $questionsCount

                ────────────────────

                Черновик сохранен. Вы можете отредактировать и опубликовать вакансию позже в разделе "📋 Мои вакансии".
            """.cleanMessage()
        }
    }

    private fun saveVacancy(message: IncomingMessage, status: VacancyStatus): String {
        // Получаем business ID пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Ошибка: пользователь не найден"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ Ошибка: бизнес не найден"
        }

        val businessId = businessUsers.first().businessId

        // Получаем данные из контекста
        val title = stateManager.getContextValue<String>(message.userId, "title") ?: ""
        val description = stateManager.getContextValue<String>(message.userId, "description") ?: ""
        val location = stateManager.getContextValue<String>(message.userId, "location")
        val salary = stateManager.getContextValue<String>(message.userId, "salary")

        // Создаем вакансию
        val vacancy = vacancyDbService.createVacancy(
            businessId = businessId,
            title = title,
            description = description,
            location = location,
            salary = salary,
            status = status
        )

        // Сохраняем ID вакансии для дальнейшего использования
        stateManager.setContextValue(message.userId, "vacancyId", vacancy.id!!)
        stateManager.setContextValue(message.userId, "currentVacancyId", vacancy.id!!)
        stateManager.setContextValue(message.userId, "vacancyCode", vacancy.code)
        stateManager.setContextValue(message.userId, "vacancyTitle", vacancy.title)
        stateManager.setContextValue(message.userId, "vacancyStatus", status.name)
        stateManager.setContextValue(message.userId, "addingMoreQuestions", false)

        // Переходим к выбору анкеты
        stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_QUESTIONNAIRE_CHOICE)

        return """
            ✅ Вакансия сохранена!

            🆔 Код вакансии: ${vacancy.code}
            📋 Название: ${vacancy.title}

            ────────────────────

            📝 Теперь настройте анкету для соискателей

            По умолчанию будут добавлены 3 базовых вопроса:
            1. Как вас зовут? (текст, обязательный)
            2. Ваш номер телефона (телефон, обязательный)
            3. Сколько вам лет? (число, необязательный)

            Выберите действие:
            • Отправьте "Только базовые" - использовать только 3 базовых вопроса
            • Отправьте "Добавить свои" - добавить дополнительные вопросы
            • Отправьте "Пропустить" - завершить без анкеты (можно добавить позже)
        """.cleanMessage()
    }

    private fun handleAllApplications(message: IncomingMessage): String {
        // Получаем бизнес пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId

        // Получаем все вакансии бизнеса
        val vacancies = vacancyDbService.findByBusinessId(businessId)
        if (vacancies.isEmpty()) {
            return """
                👥 Все отклики

                У вас пока нет созданных вакансий.

                Сначала создайте вакансию в разделе "➕ Новая вакансия".
            """.cleanMessage()
        }

        // Получаем все отклики для всех вакансий
        val allApplications = vacancies.flatMap { vacancy ->
            applicationDbService.findByVacancyId(vacancy.id!!)
        }

        if (allApplications.isEmpty()) {
            return """
                👥 Все отклики

                У вас пока нет откликов на вакансии.

                Как только кто-то откликнется, отклики появятся здесь.
            """.cleanMessage()
        }

        // Группируем по статусам
        val grouped = allApplications.groupBy { it.status }
        val newCount = grouped[ApplicationStatus.NEW]?.size ?: 0
        val viewedCount = grouped[ApplicationStatus.VIEWED]?.size ?: 0
        val contactedCount = grouped[ApplicationStatus.CONTACTED]?.size ?: 0
        val acceptedCount = grouped[ApplicationStatus.ACCEPTED]?.size ?: 0
        val rejectedCount = grouped[ApplicationStatus.REJECTED]?.size ?: 0

        val result = buildString {
            appendLine("👥 Все отклики (${allApplications.size})")
            appendLine()
            appendLine("📊 Статистика:")
            appendLine("🆕 Новых: $newCount")
            appendLine("👀 Просмотрено: $viewedCount")
            appendLine("📞 Связались: $contactedCount")
            appendLine("✅ Принято: $acceptedCount")
            appendLine("❌ Отклонено: $rejectedCount")
            appendLine()
            appendLine("────────────────────")
            appendLine()

            // Показываем последние 10 откликов
            val recentApplications = allApplications.sortedByDescending { it.createdAt }.take(10)
            appendLine("📋 Последние отклики:")
            appendLine()

            recentApplications.forEachIndexed { index, app ->
                val vacancy = vacancyDbService.findById(app.vacancyId)
                val applicant = userDbService.findById(app.userId)
                val statusEmoji = when (app.status) {
                    ApplicationStatus.NEW -> "🆕"
                    ApplicationStatus.VIEWED -> "👀"
                    ApplicationStatus.CONTACTED -> "📞"
                    ApplicationStatus.ACCEPTED -> "✅"
                    ApplicationStatus.REJECTED -> "❌"
                }

                appendLine("${index + 1}. $statusEmoji ID: ${app.id}")
                appendLine("   📋 ${vacancy?.title ?: "???"}")
                appendLine("   👤 ${applicant?.firstName ?: "???"}")
                appendLine("   📅 ${app.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                appendLine()
            }

            if (allApplications.size > 10) {
                appendLine("...и еще ${allApplications.size - 10} откликов")
                appendLine()
            }

            appendLine("────────────────────")
            appendLine()
            appendLine("Для просмотра отклика отправьте его ID (например: 5)")
        }

        return result
    }

    private fun handleStatistics(message: IncomingMessage): String {
        // Получаем бизнес пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val business = businessDbService.findById(businessId)
            ?: return "❌ Бизнес не найден"

        // Получаем все вакансии
        val allVacancies = vacancyDbService.findByBusinessId(businessId)

        // Статистика по вакансиям
        val activeVacancies = allVacancies.count { it.status == VacancyStatus.ACTIVE }
        val draftVacancies = allVacancies.count { it.status == VacancyStatus.DRAFT }
        val pausedVacancies = allVacancies.count { it.status == VacancyStatus.PAUSED }
        val closedVacancies = allVacancies.count { it.status == VacancyStatus.CLOSED }

        // Получаем все отклики
        val allApplications = allVacancies.flatMap { applicationDbService.findByVacancyId(it.id!!) }

        // Статистика по откликам
        val newApplications = allApplications.count { it.status == ApplicationStatus.NEW }
        val viewedApplications = allApplications.count { it.status == ApplicationStatus.VIEWED }
        val contactedApplications = allApplications.count { it.status == ApplicationStatus.CONTACTED }
        val acceptedApplications = allApplications.count { it.status == ApplicationStatus.ACCEPTED }
        val rejectedApplications = allApplications.count { it.status == ApplicationStatus.REJECTED }

        // Считаем конверсию
        val conversionRate = if (allApplications.isNotEmpty()) {
            (acceptedApplications.toDouble() / allApplications.size * 100).toInt()
        } else 0

        val result = buildString {
            appendLine("📊 Статистика: ${business.name}")
            appendLine()
            appendLine("════════════════════")
            appendLine()
            appendLine("📋 ВАКАНСИИ")
            appendLine()
            appendLine("Всего создано: ${allVacancies.size}")
            appendLine("✅ Активных: $activeVacancies")
            appendLine("📝 Черновиков: $draftVacancies")
            appendLine("⏸ Приостановленных: $pausedVacancies")
            appendLine("🔒 Закрытых: $closedVacancies")
            appendLine()
            appendLine("════════════════════")
            appendLine()
            appendLine("👥 ОТКЛИКИ")
            appendLine()
            appendLine("Всего получено: ${allApplications.size}")
            appendLine()
            appendLine("По статусам:")
            appendLine("🆕 Новых: $newApplications")
            appendLine("👀 Просмотрено: $viewedApplications")
            appendLine("📞 Связались: $contactedApplications")
            appendLine("✅ Принято: $acceptedApplications")
            appendLine("❌ Отклонено: $rejectedApplications")
            appendLine()
            appendLine("════════════════════")
            appendLine()
            appendLine("📈 КОНВЕРСИЯ")
            appendLine()
            appendLine("Процент принятых: $conversionRate%")
            if (allApplications.isNotEmpty()) {
                val avgPerVacancy = allApplications.size / allVacancies.size.coerceAtLeast(1)
                appendLine("Средний отклик/вакансия: $avgPerVacancy")
            }

            // Топ-3 вакансии по откликам
            if (allVacancies.isNotEmpty()) {
                appendLine()
                appendLine("════════════════════")
                appendLine()
                appendLine("🏆 ТОП-3 ВАКАНСИИ")
                appendLine()

                val vacanciesWithApplications = allVacancies.map { vacancy ->
                    val applications = applicationDbService.findByVacancyId(vacancy.id!!)
                    Pair(vacancy, applications.size)
                }.sortedByDescending { it.second }.take(3)

                vacanciesWithApplications.forEachIndexed { index, (vacancy, count) ->
                    val medal = when (index) {
                        0 -> "🥇"
                        1 -> "🥈"
                        2 -> "🥉"
                        else -> "${index + 1}."
                    }
                    appendLine("$medal ${vacancy.title}")
                    appendLine("   🆔 ${vacancy.code} | 👥 $count откликов")
                    appendLine()
                }
            }
        }

        return result
    }

    private fun handleSettings(message: IncomingMessage): String {
        return """
            ⚙️ Настройки бизнеса

            (Функционал в разработке)

            Здесь вы сможете:
            • Изменить название бизнеса
            • Управлять пользователями
            • Настроить уведомления
        """.cleanMessage()
    }

    private fun handleHelp(message: IncomingMessage): String {
        return """
            ❓ Помощь

            📋 Основные функции:

            1️⃣ Создание вакансий
            Нажмите "➕ Новая вакансия" для создания вакансии.
            Укажите название, описание, зарплату и условия.

            2️⃣ Настройка анкет
            При создании вакансии вы можете добавить свои вопросы для кандидатов.

            3️⃣ Просмотр откликов
            Все отклики приходят в раздел "👥 Все отклики".
            Вы можете просматривать анкеты и менять статусы.

            4️⃣ Публикация
            После создания вакансии вы получите код и ссылку для публикации.

            По вопросам обращайтесь к администратору.
        """.cleanMessage()
    }

    // ═══════════════════════════════════════════════════════════════
    // Обработка анкет (Phase 4)
    // ═══════════════════════════════════════════════════════════════

    private fun handleQuestionnaireChoice(message: IncomingMessage): String {
        val choice = message.text.trim().lowercase()
        // Проверяем, создаем новую вакансию или редактируем существующую
        val editingExisting = stateManager.getContextValue<Boolean>(message.userId, "editingExistingVacancy") ?: false
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "vacancyId")

        // Если редактируем существующую вакансию, работаем со старой логикой (сохраняем вопросы сразу в БД)
        if (editingExisting && vacancyId != null) {
            return when {
                choice.contains("только базовые") || choice.contains("базовые") -> {
                    questionDbService.createDefaultQuestions(vacancyId)
                    finishVacancyCreation(message)
                }
                choice.contains("добавить") && (choice.contains("свои") || choice.contains("еще")) -> {
                    val questionsCount = questionDbService.findByVacancyId(vacancyId).size
                    if (questionsCount == 0) {
                        questionDbService.createDefaultQuestions(vacancyId)
                    }
                    stateManager.setState(message.userId, ConversationState.QUESTION_ADD_TEXT)
                    val prefix = if (questionsCount == 0) "✅ Базовые вопросы добавлены!\n\nТеперь добавьте свои дополнительные вопросы.\n\n" else ""
                    """
                        ${prefix}➕ Добавление кастомного вопроса (шаг 1 из 3)

                        Введите текст вопроса для кандидата:
                    """.cleanMessage()
                }
                choice.contains("готово") || choice.contains("завершить") -> {
                    finishVacancyCreation(message)
                }
                choice.contains("пропустить") -> {
                    finishVacancyCreation(message)
                }
                else -> """
                    ❓ Неизвестное действие.

                    Выберите:
                    • "Только базовые" - использовать только 3 базовых вопроса
                    • "Добавить свои" - добавить дополнительные вопросы
                    • "Пропустить" - завершить без анкеты
                """.cleanMessage()
            }
        }

        // Новая вакансия - накапливаем вопросы в контексте
        return when {
            choice.contains("только базовые") || choice.contains("базовые") -> {
                // Добавляем базовые вопросы в контекст
                val pendingQuestions = stateManager.getContextValue<List<Map<String, Any>>>(message.userId, "pendingQuestions") ?: emptyList()
                val defaultQuestions = getDefaultQuestionsData()
                stateManager.setContextValue(message.userId, "pendingQuestions", pendingQuestions + defaultQuestions)
                // Показываем предпросмотр с выбором статуса
                showVacancyPreview(message)
            }
            choice.contains("добавить") && (choice.contains("свои") || choice.contains("еще")) -> {
                // Если это первый раз, добавляем базовые вопросы
                val pendingQuestions = stateManager.getContextValue<List<Map<String, Any>>>(message.userId, "pendingQuestions") ?: emptyList()
                val updatedQuestions = if (pendingQuestions.isEmpty()) {
                    pendingQuestions + getDefaultQuestionsData()
                } else {
                    pendingQuestions
                }
                stateManager.setContextValue(message.userId, "pendingQuestions", updatedQuestions)

                // Переходим к добавлению кастомного вопроса
                stateManager.setState(message.userId, ConversationState.QUESTION_ADD_TEXT)
                val prefix = if (pendingQuestions.isEmpty()) "✅ Базовые вопросы добавлены!\n\nТеперь добавьте свои дополнительные вопросы.\n\n" else ""
                """
                    ${prefix}➕ Добавление кастомного вопроса (шаг 1 из 3)

                    Введите текст вопроса для кандидата:
                """.cleanMessage()
            }
            choice.contains("готово") || choice.contains("завершить") -> {
                // Показываем предпросмотр с выбором статуса
                showVacancyPreview(message)
            }
            choice.contains("пропустить") -> {
                // Пропускаем анкету, показываем предпросмотр с выбором статуса
                showVacancyPreview(message)
            }
            else -> """
                ❓ Неизвестное действие.

                Выберите:
                • "Только базовые" - использовать только 3 базовых вопроса
                • "Добавить свои" - добавить дополнительные вопросы
                • "Пропустить" - завершить без анкеты
            """.cleanMessage()
        }
    }

    private fun handleQuestionTextInput(message: IncomingMessage): String {
        val questionText = message.text.trim()

        if (questionText.length < 5) {
            return "❌ Вопрос слишком короткий. Минимум 5 символов.\n\nВведите текст вопроса:"
        }

        // Сохраняем текст вопроса
        stateManager.setContextValue(message.userId, "questionText", questionText)

        // Переходим к выбору типа
        stateManager.setState(message.userId, ConversationState.QUESTION_ADD_TYPE)

        return """
            ✅ Текст вопроса: "$questionText"

            Шаг 2 из 3: Тип ответа

            Выберите тип ответа:
            1. TEXT - текстовый ответ
            2. NUMBER - число
            3. YES_NO - да/нет
            4. CHOICE - выбор из вариантов

            Введите номер (1, 2, 3 или 4):
        """.cleanMessage()
    }

    private fun handleQuestionTypeInput(message: IncomingMessage): String {
        val input = message.text.trim()

        val questionType = when (input) {
            "1", "TEXT", "текст" -> QuestionType.TEXT
            "2", "NUMBER", "число" -> QuestionType.NUMBER
            "3", "YES_NO", "да/нет" -> QuestionType.YES_NO
            "4", "CHOICE", "выбор" -> QuestionType.CHOICE
            else -> {
                return """
                    ❌ Неверный формат.

                    Выберите тип ответа:
                    1. TEXT - текстовый ответ
                    2. NUMBER - число
                    3. YES_NO - да/нет
                    4. CHOICE - выбор из вариантов

                    Введите номер (1, 2, 3 или 4):
                """.cleanMessage()
            }
        }

        // Сохраняем тип
        stateManager.setContextValue(message.userId, "questionType", questionType.name)

        // Если тип CHOICE, нужно запросить варианты
        if (questionType == QuestionType.CHOICE) {
            stateManager.setState(message.userId, ConversationState.QUESTION_ADD_OPTIONS)
            return """
                ✅ Тип: Выбор из вариантов

                Введите варианты ответа через запятую.

                Например: Есть опыт, Нет опыта, Готов учиться
            """.cleanMessage()
        }

        // Для остальных типов переходим к обязательности
        stateManager.setState(message.userId, ConversationState.QUESTION_ADD_REQUIRED)

        val typeRu = when (questionType) {
            QuestionType.TEXT -> "Текстовый ответ"
            QuestionType.NUMBER -> "Число"
            QuestionType.YES_NO -> "Да/Нет"
            else -> "?"
        }

        return """
            ✅ Тип: $typeRu

            Шаг 3 из 3: Обязательный вопрос?

            Сделать вопрос обязательным для заполнения?
            • Отправьте "Да" - вопрос обязательный
            • Отправьте "Нет" - вопрос необязательный
        """.cleanMessage()
    }

    private fun handleQuestionOptionsInput(message: IncomingMessage): String {
        val optionsInput = message.text.trim()

        if (optionsInput.isBlank()) {
            return "❌ Введите хотя бы один вариант ответа.\n\nВведите варианты через запятую:"
        }

        val options = optionsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        if (options.size < 2) {
            return "❌ Нужно минимум 2 варианта ответа.\n\nВведите варианты через запятую:"
        }

        // Сохраняем варианты
        stateManager.setContextValue(message.userId, "questionOptions", options)

        // Переходим к обязательности
        stateManager.setState(message.userId, ConversationState.QUESTION_ADD_REQUIRED)

        return """
            ✅ Варианты сохранены:
            ${options.mapIndexed { i, opt -> "${i + 1}. $opt" }.joinToString("\n")}

            Шаг 3 из 3: Обязательный вопрос?

            Сделать вопрос обязательным для заполнения?
            • Отправьте "Да" - вопрос обязательный
            • Отправьте "Нет" - вопрос необязательный
        """.cleanMessage()
    }

    private fun handleQuestionRequiredInput(message: IncomingMessage): String {
        val input = message.text.trim().lowercase()
        val isRequired = when {
            input.contains("да") || input == "1" || input == "yes" -> true
            input.contains("нет") || input == "0" || input == "no" -> false
            else -> {
                return """
                    ❓ Неизвестный ответ.

                    Отправьте:
                    • "Да" - вопрос обязательный
                    • "Нет" - вопрос необязательный
                """.cleanMessage()
            }
        }

        val questionText = stateManager.getContextValue<String>(message.userId, "questionText") ?: ""
        val questionTypeName = stateManager.getContextValue<String>(message.userId, "questionType") ?: "TEXT"
        val questionType = QuestionType.valueOf(questionTypeName)
        val options = stateManager.getContextValue<List<String>>(message.userId, "questionOptions")

        // Проверяем, создаем ли мы новую вакансию или редактируем существующую
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "vacancyId")
        val editingExisting = stateManager.getContextValue<Boolean>(message.userId, "editingExistingVacancy") ?: false

        if (editingExisting && vacancyId != null) {
            // Редактируем существующую вакансию - сохраняем вопрос сразу в БД
            questionDbService.createCustomQuestion(
                vacancyId = vacancyId,
                questionText = questionText,
                questionType = questionType,
                isRequired = isRequired,
                options = options
            )

            // Очищаем данные вопроса из контекста
            stateManager.setContextValue(message.userId, "questionText", "")
            stateManager.setContextValue(message.userId, "questionType", "")
            stateManager.setContextValue(message.userId, "questionOptions", emptyList<String>())

            // Предлагаем добавить еще один вопрос или завершить
            stateManager.setContextValue(message.userId, "addingMoreQuestions", true)
            stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_QUESTIONNAIRE_CHOICE)

            return """
                ✅ Вопрос добавлен!

                Хотите добавить еще один вопрос?
                • Отправьте "Добавить еще" - добавить еще один вопрос
                • Отправьте "Готово" - завершить создание вакансии
            """.cleanMessage()
        } else {
            // Создаем новую вакансию - накапливаем вопрос в контексте
            val pendingQuestions = stateManager.getContextValue<List<Map<String, Any>>>(message.userId, "pendingQuestions") ?: emptyList()

            val questionData = mutableMapOf<String, Any>(
                "questionText" to questionText,
                "questionType" to questionTypeName,
                "isRequired" to isRequired
            )

            if (options != null) {
                questionData["options"] = options
            }

            stateManager.setContextValue(message.userId, "pendingQuestions", pendingQuestions + questionData)

            // Очищаем данные вопроса из контекста
            stateManager.setContextValue(message.userId, "questionText", "")
            stateManager.setContextValue(message.userId, "questionType", "")
            stateManager.setContextValue(message.userId, "questionOptions", emptyList<String>())

            // Предлагаем добавить еще один вопрос или показать предпросмотр
            stateManager.setContextValue(message.userId, "addingMoreQuestions", true)
            stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_QUESTIONNAIRE_CHOICE)

            return """
                ✅ Вопрос добавлен!

                Хотите добавить еще один вопрос?
                • Отправьте "Добавить еще" - добавить еще один вопрос
                • Отправьте "Готово" - перейти к публикации
            """.cleanMessage()
        }
    }

    private fun finishVacancyCreation(message: IncomingMessage): String {
        val vacancyCode = stateManager.getContextValue<String>(message.userId, "vacancyCode") ?: "???"
        val vacancyTitle = stateManager.getContextValue<String>(message.userId, "vacancyTitle") ?: "???"
        val vacancyStatus = stateManager.getContextValue<String>(message.userId, "vacancyStatus") ?: "DRAFT"
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "vacancyId")
        val editingExisting = stateManager.getContextValue<Boolean>(message.userId, "editingExistingVacancy") ?: false

        // Получаем количество вопросов
        val questionsCount = if (vacancyId != null) {
            questionDbService.findByVacancyId(vacancyId).size
        } else 0

        // Если редактируем существующую вакансию, возвращаемся к её просмотру
        if (editingExisting && vacancyCode != "???") {
            return handleViewVacancyByCode(message, vacancyCode)
        }

        // Очищаем состояние
        stateManager.clearState(message.userId)

        return if (vacancyStatus == "ACTIVE") {
            """
                ✅ Вакансия полностью настроена и опубликована!

                🆔 Код вакансии: $vacancyCode
                📋 Название: $vacancyTitle
                📝 Вопросов в анкете: $questionsCount

                ────────────────────

                Соискатели могут откликнуться, отправив код: $vacancyCode

                Вы можете:
                • Посмотреть вакансию в разделе "📋 Мои вакансии"
                • Просматривать отклики в разделе "👥 Все отклики"

                Вакансия активна и доступна для откликов!
            """.cleanMessage()
        } else {
            """
                ✅ Вакансия полностью настроена и сохранена как черновик!

                🆔 Код вакансии: $vacancyCode
                📋 Название: $vacancyTitle
                📝 Вопросов в анкете: $questionsCount

                ────────────────────

                Черновик сохранен. Вы можете отредактировать и опубликовать вакансию позже в разделе "📋 Мои вакансии".
            """.cleanMessage()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Управление откликами (Phase 6)
    // ═══════════════════════════════════════════════════════════════

    private fun handleViewApplicationDetails(message: IncomingMessage, applicationId: Long): String {
        // Проверяем, что отклик существует
        val application = applicationDbService.findById(applicationId)
            ?: return "❌ Отклик с ID $applicationId не найден."

        // Проверяем, что этот отклик принадлежит бизнесу пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId

        // Получаем вакансию
        val vacancy = vacancyDbService.findById(application.vacancyId)
            ?: return "❌ Вакансия не найдена"

        // Проверяем, что вакансия принадлежит этому бизнесу
        if (vacancy.businessId != businessId) {
            return "❌ У вас нет доступа к этому отклику"
        }

        // Если статус NEW, меняем на VIEWED
        if (application.status == ApplicationStatus.NEW) {
            applicationDbService.updateStatus(applicationId, ApplicationStatus.VIEWED)
            application.status = ApplicationStatus.VIEWED
        }

        // Получаем соискателя
        val applicant = userDbService.findById(application.userId)
            ?: return "❌ Соискатель не найден"

        // Получаем все ответы с сохраненными текстами вопросов
        // Сортируем по questionOrder для правильного порядка отображения
        val answers = applicationDbService.getAnswersByApplicationId(applicationId)
            .sortedBy { it.questionOrder }

        val statusEmoji = when (application.status) {
            ApplicationStatus.NEW -> "🆕"
            ApplicationStatus.VIEWED -> "👀"
            ApplicationStatus.CONTACTED -> "📞"
            ApplicationStatus.ACCEPTED -> "✅"
            ApplicationStatus.REJECTED -> "❌"
        }

        val result = buildString {
            appendLine("$statusEmoji Отклик #$applicationId")
            appendLine()
            appendLine("📋 Вакансия: ${vacancy.title}")
            appendLine("🆔 Код: ${vacancy.code}")
            appendLine()
            appendLine("👤 Кандидат:")
            appendLine("   • Имя: ${applicant.firstName ?: "???"} ${applicant.lastName ?: ""}")
            if (applicant.username != null) {
                appendLine("   • Username: @${applicant.username}")
            }
            appendLine("   • ID: ${applicant.telegramId}")
            appendLine()
            appendLine("📅 Дата отклика: ${application.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
            appendLine("📊 Статус: ${application.status.name}")
            appendLine()
            appendLine("────────────────────")
            appendLine()
            appendLine("📝 Ответы на анкету:")
            appendLine()

            if (answers.isEmpty()) {
                appendLine("(Анкета не заполнялась)")
            } else {
                // Используем сохраненный текст вопроса из answer (snapshot на момент отклика)
                // Это гарантирует, что мы видим ту же формулировку, на которую отвечал кандидат,
                // даже если вопрос был изменен или удален
                answers.forEach { answer ->
                    appendLine("❓ ${answer.questionText}")
                    appendLine("   ✏️ ${answer.answerText}")
                    appendLine()
                }
            }

            if (application.notes != null) {
                appendLine("────────────────────")
                appendLine()
                appendLine("📌 Заметки:")
                appendLine(application.notes)
                appendLine()
            }

            appendLine("────────────────────")
            appendLine()
            appendLine("Действия:")
            appendLine("• Отправьте \"Статус\" - изменить статус отклика")
            appendLine("• Отправьте \"Заметка\" - добавить/изменить заметку")
            appendLine("• Отправьте \"◀️ Назад\" - вернуться к списку откликов")
        }

        // Сохраняем ID отклика в контекст для дальнейших действий
        stateManager.setContextValue(message.userId, "currentApplicationId", applicationId)
        stateManager.setState(message.userId, ConversationState.VIEWING_APPLICATION_DETAILS)

        return result
    }

    private fun handleApplicationAction(message: IncomingMessage): String {
        val action = message.text.trim().lowercase()

        return when {
            action.contains("статус") -> {
                stateManager.setState(message.userId, ConversationState.CHANGING_APPLICATION_STATUS)
                """
                    📊 Изменение статуса отклика

                    Выберите новый статус:
                    1. 🆕 NEW - новый отклик
                    2. 👀 VIEWED - просмотрено
                    3. 📞 CONTACTED - связались с кандидатом
                    4. ✅ ACCEPTED - кандидат принят
                    5. ❌ REJECTED - отказ

                    Отправьте номер (1-5) или название статуса
                """.cleanMessage()
            }
            action.contains("заметка") -> {
                stateManager.setState(message.userId, ConversationState.ADDING_APPLICATION_NOTES)
                """
                    📌 Добавление заметки

                    Введите текст заметки для этого отклика:

                    (Это может быть любая информация: результаты собеседования, договоренности, причина отказа и т.д.)
                """.cleanMessage()
            }
            action.contains("назад") || action == "◀️ назад" -> {
                // Проверяем, откуда пришел пользователь
                val fromVacancyApplications = stateManager.getContextValue<Boolean>(message.userId, "fromVacancyApplications") ?: false
                val vacancyId = stateManager.getContextValue<Long>(message.userId, "viewingVacancyId")

                if (fromVacancyApplications && vacancyId != null) {
                    // Возвращаемся к откликам конкретной вакансии
                    stateManager.setState(message.userId, ConversationState.VIEWING_VACANCY_APPLICATIONS)
                    stateManager.setContextValue(message.userId, "fromVacancyApplications", false)
                    handleVacancyApplications(message, vacancyId)
                } else {
                    // Возвращаемся ко всем откликам
                    stateManager.clearState(message.userId)
                    handleAllApplications(message)
                }
            }
            else -> """
                ❓ Неизвестная команда.

                Доступные действия:
                • "Статус" - изменить статус
                • "Заметка" - добавить заметку
                • "◀️ Назад" - вернуться к списку
            """.cleanMessage()
        }
    }

    private fun handleStatusChange(message: IncomingMessage): String {
        val applicationId = stateManager.getContextValue<Long>(message.userId, "currentApplicationId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: отклик не найден"
        }

        val input = message.text.trim().lowercase()

        val newStatus = when {
            input == "1" || input.contains("new") || input.contains("новый") -> ApplicationStatus.NEW
            input == "2" || input.contains("viewed") || input.contains("просмотрен") -> ApplicationStatus.VIEWED
            input == "3" || input.contains("contacted") || input.contains("связал") -> ApplicationStatus.CONTACTED
            input == "4" || input.contains("accepted") || input.contains("принят") || input.contains("принять") -> ApplicationStatus.ACCEPTED
            input == "5" || input.contains("rejected") || input.contains("отказ") || input.contains("отклон") -> ApplicationStatus.REJECTED
            else -> {
                return """
                    ❌ Неверный формат.

                    Выберите новый статус:
                    1. 🆕 NEW - новый отклик
                    2. 👀 VIEWED - просмотрено
                    3. 📞 CONTACTED - связались
                    4. ✅ ACCEPTED - принят
                    5. ❌ REJECTED - отказ

                    Отправьте номер (1-5) или название
                """.cleanMessage()
            }
        }

        // Обновляем статус
        val updated = applicationDbService.updateStatus(applicationId, newStatus)
        if (updated == null) {
            stateManager.clearState(message.userId)
            return "❌ Ошибка при обновлении статуса"
        }

        // Возвращаемся к просмотру деталей
        stateManager.setState(message.userId, ConversationState.VIEWING_APPLICATION_DETAILS)

        val statusEmoji = when (newStatus) {
            ApplicationStatus.NEW -> "🆕"
            ApplicationStatus.VIEWED -> "👀"
            ApplicationStatus.CONTACTED -> "📞"
            ApplicationStatus.ACCEPTED -> "✅"
            ApplicationStatus.REJECTED -> "❌"
        }

        return """
            ✅ Статус изменен на: $statusEmoji ${newStatus.name}

            Отправьте ID отклика для просмотра деталей
            или "◀️ Назад" для возврата к списку
        """.cleanMessage()
    }

    private fun handleNotesInput(message: IncomingMessage): String {
        val applicationId = stateManager.getContextValue<Long>(message.userId, "currentApplicationId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: отклик не найден"
        }

        val notes = message.text.trim()

        if (notes.isEmpty()) {
            return "❌ Заметка не может быть пустой. Введите текст заметки:"
        }

        // Сохраняем заметку
        val updated = applicationDbService.addNotes(applicationId, notes)
        if (updated == null) {
            stateManager.clearState(message.userId)
            return "❌ Ошибка при сохранении заметки"
        }

        // Автоматически показываем обновленные детали отклика
        return handleViewApplicationDetails(message, applicationId)
    }

    // ═══════════════════════════════════════════════════════════════
    // Управление вакансиями (Enhanced UX)
    // ═══════════════════════════════════════════════════════════════

    private fun handleViewVacancyByCode(message: IncomingMessage, code: String): String {
        // Получаем вакансию по коду
        val vacancy = vacancyDbService.findByCode(code)
            ?: return "❌ Вакансия с кодом $code не найдена."

        // Проверяем, что вакансия принадлежит бизнесу пользователя
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId

        if (vacancy.businessId != businessId) {
            return "❌ У вас нет доступа к этой вакансии"
        }

        // Получаем статистику
        val applicationsCount = applicationDbService.findByVacancyId(vacancy.id!!).size
        val questionsCount = questionDbService.findByVacancyId(vacancy.id).size
        val newApplications = applicationDbService.findByVacancyId(vacancy.id)
            .count { it.status == ApplicationStatus.NEW }

        val statusEmoji = when (vacancy.status) {
            VacancyStatus.ACTIVE -> "✅"
            VacancyStatus.DRAFT -> "📝"
            VacancyStatus.PAUSED -> "⏸"
            VacancyStatus.CLOSED -> "🔒"
        }

        val result = buildString {
            appendLine("$statusEmoji Вакансия ${vacancy.code}")
            appendLine()
            appendLine("📋 Название: ${vacancy.title}")
            appendLine()
            appendLine("📄 Описание:")
            appendLine(vacancy.description)
            appendLine()
            appendLine("📍 Место: ${vacancy.location ?: "не указано"}")
            appendLine("💰 Зарплата: ${vacancy.salary ?: "не указана"}")
            appendLine()
            appendLine("📊 Статус: ${vacancy.status.name}")
            if (vacancy.publishedAt != null) {
                appendLine("📅 Опубликована: ${vacancy.publishedAt?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
            }
            appendLine("📅 Создана: ${vacancy.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
            appendLine()
            appendLine("────────────────────")
            appendLine()
            appendLine("📊 Статистика:")
            appendLine("• Откликов: $applicationsCount")
            appendLine("• Новых откликов: $newApplications")
            appendLine("• Вопросов в анкете: $questionsCount")
            appendLine()
            appendLine("────────────────────")
            appendLine()
            appendLine("Действия:")
            appendLine("• Отправьте \"Редактировать\" - редактировать вакансию")
            appendLine("• Отправьте \"Анкета\" - управление вопросами анкеты")
            appendLine("• Отправьте \"Статус\" - изменить статус вакансии")
            appendLine("• Отправьте \"Отклики\" - просмотреть все отклики на эту вакансию")
            appendLine("• Отправьте \"◀️ Назад\" - вернуться к списку вакансий")
        }

        // Сохраняем ID вакансии в контекст
        stateManager.setContextValue(message.userId, "currentVacancyId", vacancy.id)
        stateManager.setState(message.userId, ConversationState.VIEWING_VACANCY_DETAILS)

        return result
    }

    private fun handleVacancyAction(message: IncomingMessage): String {
        val action = message.text.trim()

        return when {
            action.contains("✏️") || action.contains("Редактировать") || action.contains("редактировать") || action.contains("изменить") -> {
                stateManager.setState(message.userId, ConversationState.VACANCY_EDIT_CHOOSE_FIELD)
                """
                    ✏️ Редактирование вакансии

                    Выберите поле для редактирования:
                    1. Название
                    2. Описание
                    3. Место работы
                    4. Зарплата

                    Отправьте номер (1-4) или название поля
                """.cleanMessage()
            }
            action.contains("📝") || action.contains("Анкета") || action.contains("анкета") || action.contains("вопрос") -> {
                handleQuestionnaireManagement(message)
            }
            action.contains("🔄") || action.contains("Статус") || action.contains("статус") -> {
                stateManager.setState(message.userId, ConversationState.VACANCY_CHANGE_STATUS)
                """
                    🔄 Изменение статуса вакансии

                    Выберите новый статус:
                    1. ✅ ACTIVE - активна (доступна для откликов)
                    2. 📝 DRAFT - черновик (не видна соискателям)
                    3. ⏸ PAUSED - приостановлена
                    4. 🔒 CLOSED - закрыта

                    Отправьте номер (1-4) или название статуса
                """.cleanMessage()
            }
            action.contains("👥") || action.contains("Отклик") || action.contains("отклик") -> {
                val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
                    stateManager.clearState(message.userId)
                    return "❌ Ошибка: вакансия не найдена"
                }

                // Устанавливаем состояние просмотра откликов вакансии
                stateManager.setState(message.userId, ConversationState.VIEWING_VACANCY_APPLICATIONS)
                stateManager.setContextValue(message.userId, "viewingVacancyId", vacancyId)
                handleVacancyApplications(message, vacancyId)
            }
            action.contains("🗑") || action.contains("Удалить") || action.contains("удалить") -> {
                val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
                    stateManager.clearState(message.userId)
                    return "❌ Ошибка: вакансия не найдена"
                }

                val vacancy = vacancyDbService.findById(vacancyId) ?: run {
                    stateManager.clearState(message.userId)
                    return "❌ Ошибка: вакансия не найдена"
                }

                // Переходим к подтверждению удаления
                stateManager.setState(message.userId, ConversationState.VACANCY_DELETE_CONFIRM)

                val result = buildString {
                    appendLine("⚠️ Подтверждение удаления")
                    appendLine()
                    appendLine("Вы уверены, что хотите удалить вакансию?")
                    appendLine()
                    appendLine("📋 Название: ${vacancy.title}")
                    appendLine("🆔 Код: ${vacancy.code}")
                    appendLine()
                    appendLine("────────────────────")
                    appendLine()
                    appendLine("⚠️ ВНИМАНИЕ: Это действие необратимо!")
                    appendLine("Будут удалены:")
                    appendLine("• Вакансия")
                    appendLine("• Все вопросы анкеты")
                    appendLine("• Все отклики кандидатов")
                    appendLine()
                    appendLine("Отправьте \"Да, удалить\" для подтверждения")
                    appendLine("или \"Отмена\" для возврата")
                }
                return result
            }
            action.contains("◀️") || action.contains("Назад") || action.contains("назад") -> {
                stateManager.clearState(message.userId)
                handleMyVacancies(message)
            }
            else -> """
                ❓ Неизвестная команда.

                Доступные действия:
                • "✏️ Редактировать" - редактировать вакансию
                • "📝 Анкета" - управление анкетой
                • "🔄 Статус" - изменить статус
                • "👥 Отклики" - просмотреть отклики
                • "🗑 Удалить" - удалить вакансию
                • "◀️ Назад" - вернуться к списку
            """.cleanMessage()
        }
    }

    private fun handleVacancyEditChooseField(message: IncomingMessage): String {
        val input = message.text.trim().lowercase()

        val field = when {
            input == "1" || input.contains("название") -> "title"
            input == "2" || input.contains("описание") -> "description"
            input == "3" || input.contains("место") || input.contains("локация") -> "location"
            input == "4" || input.contains("зарплата") -> "salary"
            else -> {
                return """
                    ❌ Неверный формат.

                    Выберите поле:
                    1. Название
                    2. Описание
                    3. Место работы
                    4. Зарплата

                    Отправьте номер (1-4) или название поля
                """.cleanMessage()
            }
        }

        // Сохраняем выбранное поле
        stateManager.setContextValue(message.userId, "editField", field)
        stateManager.setState(message.userId, ConversationState.VACANCY_EDIT_INPUT_VALUE)

        val fieldRu = when (field) {
            "title" -> "Название"
            "description" -> "Описание"
            "location" -> "Место работы"
            "salary" -> "Зарплата"
            else -> "Поле"
        }

        return """
            ✏️ Редактирование: $fieldRu

            Введите новое значение:
            ${if (field == "location" || field == "salary") "\n(Отправьте \"-\" чтобы очистить поле)" else ""}
        """.cleanMessage()
    }

    private fun handleVacancyEditInputValue(message: IncomingMessage): String {
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вакансия не найдена"
        }

        val field = stateManager.getContextValue<String>(message.userId, "editField") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: поле не выбрано"
        }

        val newValue = message.text.trim()

        // Валидация
        when (field) {
            "title" -> {
                if (newValue.length < 5) {
                    return "❌ Название слишком короткое. Минимум 5 символов.\n\nВведите новое название:"
                }
                if (newValue.length > 255) {
                    return "❌ Название слишком длинное. Максимум 255 символов.\n\nВведите новое название:"
                }
            }
            "description" -> {
                if (newValue.length < 20) {
                    return "❌ Описание слишком короткое. Минимум 20 символов.\n\nВведите новое описание:"
                }
            }
        }

        // Обновляем поле
        val updated = when (field) {
            "title" -> vacancyDbService.updateTitle(vacancyId, newValue)
            "description" -> vacancyDbService.updateDescription(vacancyId, newValue)
            "location" -> vacancyDbService.updateLocation(vacancyId, if (newValue == "-") null else newValue)
            "salary" -> vacancyDbService.updateSalary(vacancyId, if (newValue == "-") null else newValue)
            else -> null
        }

        if (updated == null) {
            stateManager.clearState(message.userId)
            return "❌ Ошибка при обновлении вакансии"
        }

        val fieldRu = when (field) {
            "title" -> "Название"
            "description" -> "Описание"
            "location" -> "Место работы"
            "salary" -> "Зарплата"
            else -> "Поле"
        }

        // Возвращаемся к просмотру вакансии и показываем обновленные детали
        return handleViewVacancyByCode(message, updated.code)
    }

    private fun handleVacancyChangeStatus(message: IncomingMessage): String {
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вакансия не найдена"
        }

        val input = message.text.trim().lowercase()

        val newStatus = when {
            input == "1" || input.contains("active") || input.contains("активн") -> VacancyStatus.ACTIVE
            input == "2" || input.contains("draft") || input.contains("черновик") -> VacancyStatus.DRAFT
            input == "3" || input.contains("paused") || input.contains("приостановлен") -> VacancyStatus.PAUSED
            input == "4" || input.contains("closed") || input.contains("закрыт") -> VacancyStatus.CLOSED
            else -> {
                return """
                    ❌ Неверный формат.

                    Выберите новый статус:
                    1. ✅ ACTIVE - активна
                    2. 📝 DRAFT - черновик
                    3. ⏸ PAUSED - приостановлена
                    4. 🔒 CLOSED - закрыта

                    Отправьте номер (1-4) или название
                """.cleanMessage()
            }
        }

        // Обновляем статус
        val updated = vacancyDbService.changeStatus(vacancyId, newStatus)
        if (updated == null) {
            stateManager.clearState(message.userId)
            return "❌ Ошибка при обновлении статуса"
        }

        val statusEmoji = when (newStatus) {
            VacancyStatus.ACTIVE -> "✅"
            VacancyStatus.DRAFT -> "📝"
            VacancyStatus.PAUSED -> "⏸"
            VacancyStatus.CLOSED -> "🔒"
        }

        // Возвращаемся к просмотру вакансии и показываем обновленные детали
        return handleViewVacancyByCode(message, updated.code)
    }

    private fun handleVacancyDeleteConfirm(message: IncomingMessage): String {
        val input = message.text.trim().lowercase()

        // Проверяем отмену
        if (input.contains("отмена") || input.contains("нет")) {
            val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId")
            stateManager.setState(message.userId, ConversationState.VIEWING_VACANCY_DETAILS)

            if (vacancyId != null) {
                val vacancy = vacancyDbService.findById(vacancyId)
                if (vacancy != null) {
                    return handleViewVacancyByCode(message, vacancy.code)
                }
            }
            stateManager.clearState(message.userId)
            return "❌ Удаление отменено"
        }

        // Проверяем подтверждение
        if (!input.contains("да") || !input.contains("удалить")) {
            return """
                ⚠️ Для подтверждения отправьте: "Да, удалить"
                Для отмены отправьте: "Отмена"
            """.trimIndent()
        }

        // Получаем вакансию
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вакансия не найдена"
        }

        val vacancy = vacancyDbService.findById(vacancyId)
        val vacancyTitle = vacancy?.title ?: "Неизвестная вакансия"

        // Удаляем вакансию
        val deleted = vacancyDbService.deleteVacancy(vacancyId)

        stateManager.clearState(message.userId)

        return if (deleted) {
            """
                ✅ Вакансия успешно удалена!

                📋 Название: $vacancyTitle

                Все связанные данные (вопросы анкеты, отклики) также удалены.

                Возвращаемся к списку вакансий...
            """.trimIndent() + "\n\n" + handleMyVacancies(message)
        } else {
            """
                ❌ Ошибка при удалении вакансии

                Вакансия не найдена или уже удалена.
            """.trimIndent() + "\n\n" + handleMyVacancies(message)
        }
    }

    private fun handleVacancyApplications(message: IncomingMessage, vacancyId: Long): String {
        val vacancy = vacancyDbService.findById(vacancyId)
            ?: return "❌ Вакансия не найдена"

        val applications = applicationDbService.findByVacancyId(vacancyId)

        if (applications.isEmpty()) {
            // Остаемся в состоянии просмотра откликов, чтобы можно было ввести ID или вернуться назад
            val result = buildString {
                appendLine("👥 Отклики на вакансию \"${vacancy.title}\"")
                appendLine("🆔 Код: ${vacancy.code}")
                appendLine()
                appendLine("📊 Статистика:")
                appendLine("Откликов пока нет")
                appendLine()
                appendLine("────────────────────")
                appendLine()
                appendLine("Когда появятся отклики, вы сможете:")
                appendLine("• Отправить ID отклика для просмотра деталей")
                appendLine("• Отправить \"◀️ Назад\" для возврата к вакансии")
            }
            return result
        }

        // Группируем по статусам
        val grouped = applications.groupBy { it.status }
        val newCount = grouped[ApplicationStatus.NEW]?.size ?: 0
        val viewedCount = grouped[ApplicationStatus.VIEWED]?.size ?: 0
        val contactedCount = grouped[ApplicationStatus.CONTACTED]?.size ?: 0
        val acceptedCount = grouped[ApplicationStatus.ACCEPTED]?.size ?: 0
        val rejectedCount = grouped[ApplicationStatus.REJECTED]?.size ?: 0

        val result = buildString {
            appendLine("👥 Отклики на вакансию \"${vacancy.title}\"")
            appendLine("🆔 Код: ${vacancy.code}")
            appendLine()
            appendLine("📊 Статистика:")
            appendLine("🆕 Новых: $newCount")
            appendLine("👀 Просмотрено: $viewedCount")
            appendLine("📞 Связались: $contactedCount")
            appendLine("✅ Принято: $acceptedCount")
            appendLine("❌ Отклонено: $rejectedCount")
            appendLine()
            appendLine("────────────────────")
            appendLine()

            // Показываем последние 10 откликов
            val recentApplications = applications.sortedByDescending { it.createdAt }.take(10)
            appendLine("📋 Отклики:")
            appendLine()

            recentApplications.forEachIndexed { index, app ->
                val applicant = userDbService.findById(app.userId)
                val statusEmoji = when (app.status) {
                    ApplicationStatus.NEW -> "🆕"
                    ApplicationStatus.VIEWED -> "👀"
                    ApplicationStatus.CONTACTED -> "📞"
                    ApplicationStatus.ACCEPTED -> "✅"
                    ApplicationStatus.REJECTED -> "❌"
                }

                appendLine("${index + 1}. $statusEmoji ID: ${app.id}")
                appendLine("   👤 ${applicant?.firstName ?: "???"}")
                appendLine("   📅 ${app.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                appendLine()
            }

            if (applications.size > 10) {
                appendLine("...и еще ${applications.size - 10} откликов")
                appendLine()
            }

            appendLine("────────────────────")
            appendLine()
            appendLine("💡 Действия:")
            appendLine("• Отправьте ID отклика для просмотра деталей")
            appendLine("• Нажмите \"📥 Экспорт в Excel\" для выгрузки всех откликов")
            appendLine("• Отправьте \"◀️ Назад\" для возврата к вакансии")
        }

        return result
    }

    private fun handleVacancyApplicationInput(message: IncomingMessage): String {
        val input = message.text.trim().lowercase()

        // Обработка кнопки "Назад" - возвращаемся к интерфейсу вакансии
        if (input.contains("назад") || input == "◀️ назад") {
            val vacancyId = stateManager.getContextValue<Long>(message.userId, "viewingVacancyId")
            if (vacancyId != null) {
                // Возвращаемся к просмотру вакансии
                stateManager.setState(message.userId, ConversationState.VIEWING_VACANCY_DETAILS)
                stateManager.setContextValue(message.userId, "currentVacancyId", vacancyId)

                val vacancy = vacancyDbService.findById(vacancyId)
                if (vacancy != null) {
                    return handleViewVacancyByCode(message, vacancy.code)
                }
            }
            stateManager.clearState(message.userId)
            return handleAllApplications(message)
        }

        // Обработка кнопки "Экспорт в Excel"
        if (input.contains("экспорт") || message.text.contains("📥")) {
            return handleExportApplicationsToExcel(message)
        }

        // Попытка распарсить ID отклика
        val applicationId = input.toLongOrNull() ?: return """
            ❌ Неверный формат.

            Отправьте ID отклика (число) для просмотра деталей
            или "◀️ Назад" для возврата к списку откликов
        """.cleanMessage()

        // Проверяем, что отклик существует
        val application = applicationDbService.findById(applicationId) ?: return """
            ❌ Отклик с ID $applicationId не найден

            Отправьте существующий ID отклика
            или "◀️ Назад" для возврата к списку откликов
        """.cleanMessage()

        // Проверяем, что отклик принадлежит вакансии, которую просматриваем
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "viewingVacancyId")
        if (vacancyId != null && application.vacancyId != vacancyId) {
            return """
                ❌ Отклик $applicationId не относится к просматриваемой вакансии

                Отправьте ID отклика из списка выше
                или "◀️ Назад" для возврата к списку откликов
            """.cleanMessage()
        }

        // Сохраняем флаг, что пришли из просмотра откликов вакансии
        stateManager.setContextValue(message.userId, "fromVacancyApplications", true)

        // Показываем детали отклика
        return handleViewApplicationDetails(message, applicationId)
    }

    // ═══════════════════════════════════════════════════════════════
    // Управление анкетами (Enhanced UX)
    // ═══════════════════════════════════════════════════════════════

    private fun handleQuestionnaireManagement(message: IncomingMessage): String {
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вакансия не найдена"
        }

        val questions = questionDbService.findByVacancyId(vacancyId)

        val result = buildString {
            appendLine("📝 Управление анкетой")
            appendLine()

            if (questions.isEmpty()) {
                appendLine("У этой вакансии пока нет вопросов в анкете.")
                appendLine()
            } else {
                appendLine("Вопросы в анкете (${questions.size}):")
                appendLine()

                questions.forEachIndexed { index, question ->
                    val typeRu = when (question.questionType) {
                        QuestionType.TEXT -> "Текст"
                        QuestionType.NUMBER -> "Число"
                        QuestionType.YES_NO -> "Да/Нет"
                        QuestionType.CHOICE -> "Выбор"
                        QuestionType.PHONE -> "Телефон"
                        QuestionType.DATE -> "Дата"
                    }

                    val required = if (question.isRequired) "обязат." else "необязат."

                    appendLine("${index + 1}. ${question.questionText}")
                    appendLine("   Тип: $typeRu | $required")

                    if (question.questionType == QuestionType.CHOICE && question.options != null) {
                        val options = questionDbService.parseOptions(question.options)
                        if (options != null) {
                            appendLine("   Варианты: ${options.joinToString(", ")}")
                        }
                    }
                    appendLine()
                }
            }

            appendLine("────────────────────")
            appendLine()
            appendLine("Действия:")
            appendLine("• Отправьте \"Добавить\" - добавить новый вопрос")
            if (questions.isNotEmpty()) {
                appendLine("• Отправьте \"Удалить\" - удалить вопрос по номеру")
                appendLine("• Отправьте \"Редактировать\" - изменить вопрос")
            }
            appendLine("• Отправьте \"◀️ Назад\" - вернуться к вакансии")
        }

        stateManager.setState(message.userId, ConversationState.QUESTIONNAIRE_MANAGEMENT_MENU)
        return result
    }

    private fun handleQuestionnaireManagementAction(message: IncomingMessage): String {
        val action = message.text.trim().lowercase()

        return when {
            action.contains("добавить") -> {
                // Устанавливаем vacancyId для создания вопроса
                val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId")
                if (vacancyId != null) {
                    stateManager.setContextValue(message.userId, "vacancyId", vacancyId)
                    stateManager.setContextValue(message.userId, "editingExistingVacancy", true)

                    // Сохраняем данные вакансии для возврата
                    val vacancy = vacancyDbService.findById(vacancyId)
                    if (vacancy != null) {
                        stateManager.setContextValue(message.userId, "vacancyCode", vacancy.code)
                        stateManager.setContextValue(message.userId, "vacancyTitle", vacancy.title)
                        stateManager.setContextValue(message.userId, "vacancyStatus", vacancy.status.name)
                    }
                }
                stateManager.setState(message.userId, ConversationState.QUESTION_ADD_TEXT)
                """
                    ➕ Добавление нового вопроса (шаг 1 из 3)

                    Введите текст вопроса для кандидата:
                """.cleanMessage()
            }
            action.contains("удалить") -> {
                stateManager.setState(message.userId, ConversationState.QUESTIONNAIRE_DELETE_ENTER_NUMBER)
                """
                    🗑 Удаление вопроса

                    Введите номер вопроса для удаления (см. список выше):
                """.cleanMessage()
            }
            action.contains("редактировать") || action.contains("изменить") -> {
                stateManager.setState(message.userId, ConversationState.QUESTIONNAIRE_EDIT_CHOOSE_QUESTION)
                """
                    ✏️ Редактирование вопроса

                    Введите номер вопроса для редактирования (см. список выше):
                """.cleanMessage()
            }
            action.contains("заполнить заново") || action.contains("🔄") -> {
                val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
                    stateManager.clearState(message.userId)
                    return "❌ Ошибка: вакансия не найдена"
                }

                // Получаем вакансию для сохранения её данных
                val vacancy = vacancyDbService.findById(vacancyId) ?: run {
                    stateManager.clearState(message.userId)
                    return "❌ Ошибка: вакансия не найдена"
                }

                // Удаляем все существующие вопросы
                questionDbService.deleteByVacancyId(vacancyId)

                // Переходим к выбору анкеты, сохраняя информацию о вакансии
                stateManager.setContextValue(message.userId, "vacancyId", vacancyId)
                stateManager.setContextValue(message.userId, "vacancyCode", vacancy.code)
                stateManager.setContextValue(message.userId, "vacancyTitle", vacancy.title)
                stateManager.setContextValue(message.userId, "vacancyStatus", vacancy.status.name)
                stateManager.setContextValue(message.userId, "addingMoreQuestions", false)
                stateManager.setContextValue(message.userId, "editingExistingVacancy", true)
                stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_QUESTIONNAIRE_CHOICE)

                """
                    🔄 Анкета очищена!

                    Все вопросы удалены. Теперь создайте новую анкету.

                    Выберите действие:
                    • Отправьте "Только базовые" - добавить 3 базовых вопроса
                    • Отправьте "Добавить свои" - создать кастомные вопросы
                    • Отправьте "Пропустить" - оставить без вопросов
                """.cleanMessage()
            }
            action.contains("назад") || action == "◀️ назад" -> {
                stateManager.setState(message.userId, ConversationState.VIEWING_VACANCY_DETAILS)
                val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId")
                if (vacancyId != null) {
                    val vacancy = vacancyDbService.findById(vacancyId)
                    if (vacancy != null) {
                        return handleViewVacancyByCode(message, vacancy.code)
                    }
                }
                handleMyVacancies(message)
            }
            else -> """
                ❓ Неизвестная команда.

                Доступные действия:
                • "Добавить" - добавить вопрос
                • "Удалить" - удалить вопрос
                • "Редактировать" - изменить вопрос
                • "◀️ Назад" - вернуться к вакансии
            """.cleanMessage()
        }
    }

    private fun handleQuestionDeleteByNumber(message: IncomingMessage): String {
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вакансия не найдена"
        }

        val number = message.text.trim().toIntOrNull()
        if (number == null || number < 1) {
            return "❌ Введите корректный номер вопроса (число больше 0)"
        }

        val questions = questionDbService.findByVacancyId(vacancyId)
        if (number > questions.size) {
            return "❌ Вопрос с номером $number не найден. Всего вопросов: ${questions.size}"
        }

        val questionToDelete = questions[number - 1]
        questionDbService.deleteById(questionToDelete.id!!)

        stateManager.setState(message.userId, ConversationState.QUESTIONNAIRE_MANAGEMENT_MENU)

        return """
            ✅ Вопрос "${ questionToDelete.questionText}" удален!

            Отправьте любое сообщение для просмотра обновленного списка вопросов
        """.cleanMessage()
    }

    private fun handleQuestionEditChooseQuestion(message: IncomingMessage): String {
        val vacancyId = stateManager.getContextValue<Long>(message.userId, "currentVacancyId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вакансия не найдена"
        }

        val number = message.text.trim().toIntOrNull()
        if (number == null || number < 1) {
            return "❌ Введите корректный номер вопроса (число больше 0)"
        }

        val questions = questionDbService.findByVacancyId(vacancyId)
        if (number > questions.size) {
            return "❌ Вопрос с номером $number не найден. Всего вопросов: ${questions.size}"
        }

        val question = questions[number - 1]

        // Сохраняем ID вопроса
        stateManager.setContextValue(message.userId, "currentQuestionId", question.id!!)
        stateManager.setState(message.userId, ConversationState.QUESTIONNAIRE_EDIT_CHOOSE_FIELD)

        return """
            ✏️ Редактирование вопроса

            Вопрос: ${question.questionText}
            Тип: ${question.questionType.name}
            Обязательный: ${if (question.isRequired) "Да" else "Нет"}

            Что хотите изменить?
            1. Текст вопроса
            2. Обязательность (да/нет)
            ${if (question.questionType == QuestionType.CHOICE) "3. Варианты ответа" else ""}

            Отправьте номер поля для редактирования
        """.cleanMessage()
    }

    private fun handleQuestionEditChooseField(message: IncomingMessage): String {
        val questionId = stateManager.getContextValue<Long>(message.userId, "currentQuestionId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вопрос не найден"
        }

        val question = questionDbService.findById(questionId) ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вопрос не найден"
        }

        val input = message.text.trim().lowercase()

        val field = when {
            input == "1" || input.contains("текст") -> "text"
            input == "2" || input.contains("обязат") -> "required"
            input == "3" || input.contains("вариант") -> {
                if (question.questionType == QuestionType.CHOICE) "options" else null
            }
            else -> null
        }

        if (field == null) {
            return """
                ❌ Неверный формат.

                Выберите поле:
                1. Текст вопроса
                2. Обязательность
                ${if (question.questionType == QuestionType.CHOICE) "3. Варианты ответа" else ""}

                Отправьте номер поля
            """.cleanMessage()
        }

        stateManager.setContextValue(message.userId, "editQuestionField", field)
        stateManager.setState(message.userId, ConversationState.QUESTIONNAIRE_EDIT_INPUT_VALUE)

        return when (field) {
            "text" -> "Введите новый текст вопроса:"
            "required" -> "Сделать вопрос обязательным? (Да/Нет)"
            "options" -> "Введите новые варианты ответа через запятую:"
            else -> "Введите новое значение:"
        }
    }

    private fun handleQuestionEditInputValue(message: IncomingMessage): String {
        val questionId = stateManager.getContextValue<Long>(message.userId, "currentQuestionId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вопрос не найден"
        }

        val field = stateManager.getContextValue<String>(message.userId, "editQuestionField") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: поле не выбрано"
        }

        val input = message.text.trim()

        val updated = when (field) {
            "text" -> {
                if (input.length < 5) {
                    return "❌ Текст слишком короткий. Минимум 5 символов.\n\nВведите новый текст вопроса:"
                }
                questionDbService.updateQuestionText(questionId, input)
            }
            "required" -> {
                val isRequired = when {
                    input.lowercase().contains("да") || input == "1" -> true
                    input.lowercase().contains("нет") || input == "0" -> false
                    else -> {
                        return "❌ Введите \"Да\" или \"Нет\""
                    }
                }
                questionDbService.updateQuestionRequired(questionId, isRequired)
            }
            "options" -> {
                val options = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (options.size < 2) {
                    return "❌ Нужно минимум 2 варианта ответа.\n\nВведите варианты через запятую:"
                }
                questionDbService.updateQuestionOptions(questionId, options)
            }
            else -> null
        }

        if (updated == null) {
            stateManager.clearState(message.userId)
            return "❌ Ошибка при обновлении вопроса"
        }

        stateManager.setState(message.userId, ConversationState.QUESTIONNAIRE_MANAGEMENT_MENU)

        return """
            ✅ Вопрос обновлен!

            Отправьте любое сообщение для просмотра обновленного списка вопросов
        """.cleanMessage()
    }

    // ═══════════════════════════════════════════════════════════════
    // Вспомогательные функции для нового UX создания вакансии
    // ═══════════════════════════════════════════════════════════════

    /**
     * Возвращает данные базовых вопросов в виде Map для накопления в контексте
     */
    private fun getDefaultQuestionsData(): List<Map<String, Any>> {
        return listOf(
            mapOf(
                "questionText" to "Как вас зовут?",
                "questionType" to "TEXT",
                "isRequired" to true,
                "orderIndex" to 1
            ),
            mapOf(
                "questionText" to "Ваш номер телефона",
                "questionType" to "PHONE",
                "isRequired" to true,
                "orderIndex" to 2
            ),
            mapOf(
                "questionText" to "Сколько вам лет?",
                "questionType" to "NUMBER",
                "isRequired" to false,
                "orderIndex" to 3
            )
        )
    }

    /**
     * Показывает предпросмотр вакансии с анкетой и предлагает выбрать статус (опубликовать/черновик)
     */
    private fun showVacancyPreview(message: IncomingMessage): String {
        // Получаем данные вакансии из контекста
        val title = stateManager.getContextValue<String>(message.userId, "title") ?: ""
        val description = stateManager.getContextValue<String>(message.userId, "description") ?: ""
        val location = stateManager.getContextValue<String>(message.userId, "location")
        val salary = stateManager.getContextValue<String>(message.userId, "salary")

        // Получаем накопленные вопросы
        val pendingQuestions = stateManager.getContextValue<List<Map<String, Any>>>(message.userId, "pendingQuestions") ?: emptyList()

        // Формируем предпросмотр
        val result = buildString {
            appendLine("📋 Предпросмотр вакансии")
            appendLine()
            appendLine("════════════════════")
            appendLine()
            appendLine("📌 Название:")
            appendLine(title)
            appendLine()
            appendLine("📄 Описание:")
            appendLine(description)
            appendLine()
            appendLine("📍 Место работы: ${location ?: "не указано"}")
            appendLine("💰 Зарплата: ${salary ?: "не указана"}")
            appendLine()
            appendLine("════════════════════")
            appendLine()
            appendLine("📝 Анкета для соискателей:")
            appendLine()

            if (pendingQuestions.isEmpty()) {
                appendLine("(Без вопросов)")
            } else {
                pendingQuestions.forEachIndexed { index, questionData ->
                    val questionText = questionData["questionText"] as? String ?: ""
                    val questionType = questionData["questionType"] as? String ?: ""
                    val isRequired = questionData["isRequired"] as? Boolean ?: false

                    val typeRu = when (questionType) {
                        "TEXT" -> "Текст"
                        "NUMBER" -> "Число"
                        "PHONE" -> "Телефон"
                        "YES_NO" -> "Да/Нет"
                        "CHOICE" -> "Выбор"
                        "DATE" -> "Дата"
                        else -> questionType
                    }

                    val requiredRu = if (isRequired) "обязательный" else "необязательный"

                    appendLine("${index + 1}. $questionText")
                    appendLine("   ($typeRu, $requiredRu)")

                    // Если есть опции для CHOICE
                    @Suppress("UNCHECKED_CAST")
                    val options = questionData["options"] as? List<String>
                    if (options != null && options.isNotEmpty()) {
                        appendLine("   Варианты: ${options.joinToString(", ")}")
                    }
                    appendLine()
                }
                appendLine("Всего вопросов: ${pendingQuestions.size}")
            }

            appendLine()
            appendLine("════════════════════")
            appendLine()
            appendLine("Выберите действие:")
            appendLine("• Отправьте \"Опубликовать\" - активировать вакансию")
            appendLine("• Отправьте \"Черновик\" - сохранить как черновик")
            appendLine("• Отправьте \"Отмена\" - отменить создание")
        }

        // Переходим в состояние предпросмотра
        stateManager.setState(message.userId, ConversationState.VACANCY_CREATE_PREVIEW)

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // Broadcast/Advertising Management
    // ═══════════════════════════════════════════════════════════════

    /**
     * Главное меню рекламных рассылок
     */
    private fun handleBroadcastMenu(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId

        // Получаем статистику
        val channels = broadcastDbService.findChannelsByBusinessId(businessId)
        val activeChannels = channels.count { it.isActive }
        val campaigns = broadcastDbService.findCampaignsByBusinessId(businessId)
        val sentCampaigns = campaigns.count { it.status == "SENT" }

        stateManager.setState(message.userId, ConversationState.BROADCAST_MENU)

        return """
            📢 Рекламные рассылки

            📊 Текущее состояние:
            • Чатов: ${channels.size} (активных: $activeChannels)
            • Кампаний создано: ${campaigns.size}
            • Отправлено: $sentCampaigns

            ────────────────────

            Выберите действие:

            ➕ Создать рекламу - создать новую кампанию
            📡 Мои чаты - управление чатами для рассылки
            📋 Мои рассылки - список всех кампаний

            ◀️ Назад в меню - вернуться в главное меню
        """.cleanMessage()
    }

    /**
     * Обработка действий в меню рекламы
     */
    private fun handleBroadcastMenuAction(message: IncomingMessage): String {
        return when (message.text) {
            "➕ Создать рекламу" -> handleCampaignCreateStart(message)
            "📡 Мои чаты" -> handleChannelManagement(message)
            "📋 Мои рассылки" -> handleCampaignsList(message)
            "◀️ Назад в меню", "◀️ Назад" -> {
                stateManager.clearState(message.userId)
                handleStart(message)
            }
            else -> "❓ Неизвестное действие. Используйте кнопки меню."
        }
    }

    /**
     * Начало создания рекламной кампании
     */
    private fun handleCampaignCreateStart(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId

        // Проверяем наличие активных чатов
        val activeChannels = broadcastDbService.findActiveChannelsByBusinessId(businessId)
        if (activeChannels.isEmpty()) {
            return """
                ⚠️ У вас нет активных чатов для рассылки

                Сначала добавьте чаты/группы в разделе "📡 Мои чаты", а затем создавайте рекламные кампании.

                Используйте кнопку ниже для возврата в меню рекламы.
            """.cleanMessage()
        }

        stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_CREATE_TITLE)

        return """
            ➕ Создание рекламной кампании

            Шаг 1 из 2: Название кампании

            Введите название для вашей рекламной кампании (это название видно только вам для управления):
        """.cleanMessage()
    }

    /**
     * Ввод названия кампании
     */
    private fun handleCampaignTitleInput(message: IncomingMessage): String {
        val title = message.text.trim()

        if (title.length < 3) {
            return "❌ Название слишком короткое. Минимум 3 символа.\n\nВведите название кампании:"
        }

        if (title.length > 200) {
            return "❌ Название слишком длинное. Максимум 200 символов.\n\nВведите название кампании:"
        }

        // Сохраняем название
        stateManager.setContextValue(message.userId, "campaign_title", title)
        stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_CREATE_MESSAGE)

        return """
            ✅ Название сохранено: $title

            Шаг 2 из 2: Текст сообщения

            Введите текст рекламного сообщения, которое будет отправлено в чаты:

            💡 Подсказка: Вы можете использовать форматирование Telegram (жирный, курсив, ссылки).
        """.cleanMessage()
    }

    /**
     * Ввод текста сообщения
     */
    private fun handleCampaignMessageInput(message: IncomingMessage): String {
        val messageText = message.text.trim()

        if (messageText.length < 10) {
            return "❌ Сообщение слишком короткое. Минимум 10 символов.\n\nВведите текст сообщения:"
        }

        if (messageText.length > 4096) {
            return "❌ Сообщение слишком длинное. Максимум 4096 символов.\n\nВведите текст сообщения:"
        }

        // Сохраняем текст
        stateManager.setContextValue(message.userId, "campaign_message", messageText)
        stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_PREVIEW)

        val title = stateManager.getContextValue(message.userId, "campaign_title") as? String ?: "Без названия"

        return """
            ✅ Текст сохранен

            ════════════════════
            Предпросмотр кампании
            ════════════════════

            📋 Название: $title

            📝 Текст сообщения:
            ────────────────────
            $messageText
            ────────────────────

            Выберите действие:
            • ✅ Отправить - отправить рекламу сразу во все активные чаты
            • 💾 Сохранить - сохранить без отправки (можно настроить расписание позже)
            • ✏️ Редактировать - изменить текст сообщения
            • ❌ Отмена - отменить создание кампании
        """.cleanMessage()
    }

    /**
     * Обработка действий в предпросмотре кампании
     */
    private fun handleCampaignPreviewAction(message: IncomingMessage): String {
        return when (message.text) {
            "✅ Отправить" -> handleCampaignSend(message)
            "💾 Сохранить" -> handleCampaignSave(message)
            "✏️ Редактировать" -> {
                stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_CREATE_MESSAGE)
                "✏️ Введите новый текст сообщения:"
            }
            "❌ Отмена" -> {
                stateManager.clearState(message.userId)
                "❌ Создание кампании отменено.\n\nВы вернулись в главное меню."
            }
            else -> "❓ Неизвестное действие. Используйте кнопки меню."
        }
    }

    /**
     * Сохранение кампании без отправки
     */
    private fun handleCampaignSave(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val title = stateManager.getContextValue(message.userId, "campaign_title") as? String
            ?: return "❌ Ошибка: название кампании не найдено"
        val messageText = stateManager.getContextValue(message.userId, "campaign_message") as? String
            ?: return "❌ Ошибка: текст сообщения не найден"

        // Создаем кампанию со статусом DRAFT
        val campaign = broadcastDbService.createCampaign(
            businessId = businessId,
            title = title,
            messageText = messageText,
            createdByUserId = user.id!!
        )

        // Статус по умолчанию - DRAFT
        campaign.status = "DRAFT"
        broadcastDbService.updateCampaign(campaign)

        stateManager.clearState(message.userId)

        return """
            ✅ Кампания сохранена!

            📋 Название: $title
            🆔 ID кампании: ${campaign.id}
            📊 Статус: Черновик

            Вы можете:
            • Настроить расписание отправки
            • Отправить вручную
            • Редактировать содержимое

            Найти кампанию в меню: 📢 Реклама → 📋 Мои рассылки
        """.cleanMessage()
    }

    /**
     * Отправка кампании
     */
    private fun handleCampaignSend(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val title = stateManager.getContextValue(message.userId, "campaign_title") as? String
            ?: return "❌ Ошибка: название кампании не найдено"
        val messageText = stateManager.getContextValue(message.userId, "campaign_message") as? String
            ?: return "❌ Ошибка: текст сообщения не найден"

        // Создаем кампанию
        val campaign = broadcastDbService.createCampaign(
            businessId = businessId,
            title = title,
            messageText = messageText,
            createdByUserId = user.id!!
        )

        // Отправляем сообщения в чаты
        campaign.status = "SENDING"
        broadcastDbService.updateCampaign(campaign)

        val result = broadcastService.sendCampaign(campaign)

        // Обновляем статус кампании на основе результата
        campaign.status = if (result.failedSends == 0 && result.successfulSends > 0) {
            "SENT"
        } else if (result.successfulSends > 0) {
            "SENT" // Частично отправлено, но считаем успешным
        } else {
            "FAILED"
        }
        broadcastDbService.updateCampaign(campaign)

        stateManager.clearState(message.userId)

        // Формируем детальный отчет
        val reportDetails = buildString {
            result.results.forEach { channelResult ->
                val emoji = if (channelResult.success) "✅" else "❌"
                val channelName = channelResult.channelName ?: channelResult.channelId
                appendLine("$emoji $channelName")
                if (!channelResult.success && channelResult.error != null) {
                    appendLine("   ⚠️ ${channelResult.error}")
                }
            }
        }

        return """
            ✅ Рекламная кампания отправлена!

            📋 Название: $title
            🆔 ID кампании: ${campaign.id}

            📊 Результаты отправки:
            • Всего чатов: ${result.totalChannels}
            • Успешно: ${result.successfulSends}
            • Ошибок: ${result.failedSends}

            📡 Детали:
            $reportDetails
            Вы можете отслеживать кампании в разделе "📋 Мои рассылки".

            Вы можете отслеживать статус в разделе "📋 Мои рассылки".
        """.cleanMessage()
    }

    /**
     * Список кампаний
     */
    private fun handleCampaignsList(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val campaigns = broadcastDbService.findCampaignsByBusinessId(businessId)

        // Устанавливаем состояние ожидания выбора кампании
        stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGNS_LIST)

        if (campaigns.isEmpty()) {
            return """
                📋 Мои рассылки

                У вас пока нет созданных кампаний.

                Нажмите "➕ Создать рекламу" для создания первой кампании.
            """.cleanMessage()
        }

        val result = buildString {
            appendLine("📋 Мои рассылки (${campaigns.size})")
            appendLine()

            campaigns.sortedByDescending { it.createdAt }.forEach { campaign ->
                val statusEmoji = when (campaign.status) {
                    "DRAFT" -> "📝"
                    "SENT" -> "✅"
                    "SENDING" -> "⏳"
                    "FAILED" -> "❌"
                    else -> "❓"
                }

                val statusRu = when (campaign.status) {
                    "DRAFT" -> "Черновик"
                    "SENT" -> "Отправлена"
                    "SENDING" -> "Отправляется"
                    "FAILED" -> "Ошибка"
                    else -> campaign.status
                }

                appendLine("$statusEmoji ${campaign.title}")
                appendLine("   🆔 ID: ${campaign.id}")
                appendLine("   📊 Статус: $statusRu")
                appendLine("   📅 Создана: ${campaign.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")

                // Показываем информацию о расписании
                if (campaign.scheduleEnabled) {
                    val scheduleType = when (campaign.scheduleType) {
                        "ONCE" -> "Одноразово"
                        "HOURLY" -> "Каждый час"
                        "DAILY" -> "Каждый день"
                        "CUSTOM" -> "Каждые ${campaign.scheduleIntervalHours} ч"
                        "EVERY_15_MINUTES" -> "Каждые 15 минут"
                        else -> campaign.scheduleType
                    }
                    appendLine("   ⏰ Расписание: $scheduleType")
                    campaign.nextSendAt?.let {
                        appendLine("   📅 Следующая: ${it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                    }
                }

                appendLine()
            }

            appendLine("────────────────────")
            appendLine()
            appendLine("💡 Отправьте ID кампании для просмотра деталей")
            appendLine("   или нажмите \"◀️ Назад\" для возврата в меню")
        }

        return result
    }

    /**
     * Управление чатами/группами
     */
    private fun handleChannelManagement(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val channels = broadcastDbService.findChannelsByBusinessId(businessId)

        stateManager.setState(message.userId, ConversationState.BROADCAST_CHANNEL_MANAGEMENT)

        val result = buildString {
            appendLine("📡 Мои чаты/группы")
            appendLine()

            if (channels.isEmpty()) {
                appendLine("У вас пока нет добавленных чатов.")
                appendLine()
                appendLine("Добавьте первый чат, нажав кнопку \"➕ Добавить чат\"")
            } else {
                appendLine("Всего чатов: ${channels.size}")
                appendLine()

                channels.forEach { channel ->
                    val statusEmoji = if (channel.isActive) "✅" else "⚠️"
                    val adminStatus = if (channel.isBotAdmin) "✓ Бот - админ" else "✗ Нет прав"

                    appendLine("$statusEmoji ${channel.channelName ?: channel.channelId}")
                    appendLine("   🆔 ID: ${channel.channelId}")
                    appendLine("   🔐 $adminStatus")

                    if (!channel.isActive && channel.validationError != null) {
                        appendLine("   ⚠️ ${channel.validationError}")
                    }

                    val lastValidation = channel.lastValidationAt
                    if (lastValidation != null) {
                        appendLine("   🕐 Проверен: ${lastValidation.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                    }

                    appendLine()
                }
            }

            appendLine("────────────────────")
            appendLine()
            appendLine("Выберите действие:")
            appendLine("➕ Добавить чат - добавить новый чат/группу")
            appendLine("🗑 Удалить чат - удалить чат")
            appendLine("🔄 Проверить чаты - проверить права бота")
            appendLine("◀️ Назад - вернуться в меню рекламы")
        }

        return result
    }

    /**
     * Обработка действий управления чатами
     */
    private fun handleChannelManagementAction(message: IncomingMessage): String {
        return when (message.text) {
            "➕ Добавить чат" -> handleChannelAddStart(message)
            "🗑 Удалить чат" -> handleChannelDeleteStart(message)
            "🔄 Проверить чаты" -> handleChannelValidate(message)
            "◀️ Назад" -> handleBroadcastMenu(message)
            else -> "❓ Неизвестное действие. Используйте кнопки меню."
        }
    }

    /**
     * Начало добавления чата/группы
     */
    private fun handleChannelAddStart(message: IncomingMessage): String {
        stateManager.setState(message.userId, ConversationState.BROADCAST_CHANNEL_ADD_ID)

        return """
            ➕ Добавление чата/группы

            Введите ID чата или username:

            💡 Примеры:
            • @mygroup (для публичных групп)
            • -1001234567890 (для приватных групп/чатов)

            ⚠️ Важно:
            • Бот должен быть добавлен в чат/группу как участник
            • Группа не должна иметь ограничения "только админы могут писать"

            Отправьте ID чата или нажмите "❌ Отмена"
        """.cleanMessage()
    }

    /**
     * Ввод ID канала
     */
    private fun handleChannelAddInput(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val channelId = message.text.trim()

        // Проверяем, не добавлен ли уже этот чат
        if (broadcastDbService.channelExists(businessId, channelId)) {
            return """
                ⚠️ Этот чат уже добавлен

                Введите другой ID чата или нажмите "❌ Отмена"
            """.cleanMessage()
        }

        // Валидируем доступ к чату
        val validation = broadcastDbService.validateChannelAccess(channelId)

        if (!validation.success) {
            return """
                ❌ Не удалось добавить чат

                Ошибка: ${validation.error}

                Убедитесь, что:
                • Бот добавлен в чат/группу как участник
                • Группа не ограничивает права на отправку сообщений
                • ID чата указан правильно

                Введите другой ID чата или нажмите "❌ Отмена"
            """.cleanMessage()
        }

        // Создаем канал
        val channel = broadcastDbService.createChannel(
            businessId = businessId,
            channelId = channelId,
            channelName = validation.channelName
        )

        // Обновляем информацию о валидации
        channel.isBotAdmin = true
        channel.isActive = true
        channel.channelType = validation.channelType ?: "UNKNOWN"
        channel.lastValidationAt = java.time.OffsetDateTime.now()
        broadcastDbService.updateChannel(channel)

        stateManager.clearState(message.userId)

        return """
            ✅ Чат успешно добавлен!

            📡 Название: ${validation.channelName}
            🆔 ID: $channelId
            📊 Тип: ${validation.channelType}

            Теперь вы можете использовать этот чат для рекламных рассылок.

            Используйте кнопки меню для дальнейших действий.
        """.cleanMessage()
    }

    /**
     * Начало удаления чата
     */
    private fun handleChannelDeleteStart(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val channels = broadcastDbService.findChannelsByBusinessId(businessId)

        if (channels.isEmpty()) {
            return """
                📡 У вас нет добавленных чатов

                Сначала добавьте чат, используя кнопку "➕ Добавить чат"
            """.cleanMessage()
        }

        val result = buildString {
            appendLine("🗑 Удаление чата")
            appendLine()
            appendLine("Выберите чат для удаления:")
            appendLine()

            channels.forEachIndexed { index, channel ->
                appendLine("${index + 1}. ${channel.channelName ?: channel.channelId}")
                appendLine("   🆔 ${channel.channelId}")
                appendLine()
            }

            appendLine("Отправьте номер чата (1-${channels.size}) или нажмите \"❌ Отмена\"")
        }

        // Сохраняем список чатов в контекст
        stateManager.setContextValue(message.userId, "channels_list", channels.map { it.id to it.channelName }.toMap())
        stateManager.setState(message.userId, ConversationState.BROADCAST_CHANNEL_DELETE_CONFIRM)

        return result
    }

    /**
     * Подтверждение удаления чата
     */
    private fun handleChannelDeleteConfirm(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val channels = broadcastDbService.findChannelsByBusinessId(businessId)

        val selectedNumber = message.text.trim().toIntOrNull()

        if (selectedNumber == null || selectedNumber < 1 || selectedNumber > channels.size) {
            return """
                ❌ Некорректный номер

                Отправьте номер чата (1-${channels.size}) или нажмите "❌ Отмена"
            """.cleanMessage()
        }

        val channelToDelete = channels[selectedNumber - 1]

        // Удаляем чат
        broadcastDbService.deleteChannel(channelToDelete.id!!)

        stateManager.clearState(message.userId)

        return """
            ✅ Чат удален

            📡 ${channelToDelete.channelName ?: channelToDelete.channelId}

            Чат больше не будет использоваться для рекламных рассылок.
        """.cleanMessage()
    }

    /**
     * Проверка чатов
     */
    private fun handleChannelValidate(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId
        val channels = broadcastDbService.findChannelsByBusinessId(businessId)

        if (channels.isEmpty()) {
            return """
                📡 У вас нет добавленных чатов

                Сначала добавьте чат, используя кнопку "➕ Добавить чат"
            """.cleanMessage()
        }

        val result = buildString {
            appendLine("🔄 Проверка чатов...")
            appendLine()

            channels.forEach { channel ->
                val validation = broadcastDbService.validateChannelAccess(channel.channelId)

                // Обновляем информацию о канале
                channel.isBotAdmin = validation.success
                channel.isActive = validation.success
                channel.validationError = validation.error
                channel.lastValidationAt = java.time.OffsetDateTime.now()

                if (validation.channelName != null) {
                    channel.channelName = validation.channelName
                }
                if (validation.channelType != null) {
                    channel.channelType = validation.channelType
                }

                broadcastDbService.updateChannel(channel)

                // Выводим результат
                val statusEmoji = if (validation.success) "✅" else "❌"
                appendLine("$statusEmoji ${channel.channelName ?: channel.channelId}")

                if (!validation.success) {
                    appendLine("   ⚠️ ${validation.error}")
                }

                appendLine()
            }

            val validChannels = channels.count { it.isActive }
            appendLine("────────────────────")
            appendLine()
            appendLine("📊 Результат проверки:")
            appendLine("✅ Активных чатов: $validChannels")
            appendLine("❌ Неактивных чатов: ${channels.size - validChannels}")
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // Управление деталями кампании и расписанием
    // ═══════════════════════════════════════════════════════════════

    /**
     * Обработка ввода ID кампании для просмотра деталей
     */
    private fun handleCampaignSelectById(message: IncomingMessage): String {
        // Поддержка кнопки "Назад"
        if (message.text == "◀️ Назад" || message.text == "◀️ Назад в меню") {
            return handleBroadcastMenu(message)
        }

        // Парсим ID кампании
        val campaignId = message.text.toLongOrNull()
            ?: return "❌ Неверный формат ID. Пожалуйста, отправьте числовой ID кампании."

        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Пользователь не найден в системе"

        val businessUsers = businessUserDbService.findByUserId(user.id!!)
        if (businessUsers.isEmpty()) {
            return "❌ У вас нет доступа ни к одному бизнесу"
        }

        val businessId = businessUsers.first().businessId

        // Находим кампанию
        val campaign = broadcastDbService.findCampaignById(campaignId)
            ?: return "❌ Кампания с ID $campaignId не найдена"

        // Проверяем, что кампания принадлежит бизнесу пользователя
        if (campaign.businessId != businessId) {
            return "❌ У вас нет доступа к этой кампании"
        }

        // Сохраняем ID кампании в контексте
        stateManager.setContextValue(message.userId, "selectedCampaignId", campaignId)
        stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_VIEW_DETAILS)

        // Формируем детальную информацию о кампании
        val statusEmoji = when (campaign.status) {
            "DRAFT" -> "📝"
            "SENT" -> "✅"
            "SENDING" -> "⏳"
            "FAILED" -> "❌"
            else -> "❓"
        }

        val statusRu = when (campaign.status) {
            "DRAFT" -> "Черновик"
            "SENT" -> "Отправлена"
            "SENDING" -> "Отправляется"
            "FAILED" -> "Ошибка"
            else -> campaign.status
        }

        val scheduleInfo = if (campaign.scheduleEnabled) {
            val scheduleType = when (campaign.scheduleType) {
                "ONCE" -> "Одноразово"
                "HOURLY" -> "Каждый час (в ${campaign.scheduleIntervalHours ?: 0} минут)"
                "DAILY" -> "Каждый день (в ${campaign.scheduleIntervalHours ?: 0}:00)"
                "CUSTOM" -> "Каждые ${campaign.scheduleIntervalHours} ч"
                "EVERY_15_MINUTES" -> "Каждые 15 минут"
                else -> campaign.scheduleType
            }
            val nextSend = campaign.nextSendAt?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) ?: "не задано"
            """
                ⏰ Расписание: ВКЛЮЧЕНО
                   Тип: $scheduleType
                   Следующая отправка: $nextSend
            """.trimIndent()
        } else {
            "⏰ Расписание: ВЫКЛЮЧЕНО"
        }

        val lastSentAt = campaign.lastSentAt
        val lastSent = if (lastSentAt != null) {
            "\n📅 Последняя отправка: ${lastSentAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
        } else {
            ""
        }

        return """
            $statusEmoji Детали кампании

            📌 Название: ${campaign.title}
            🆔 ID: ${campaign.id}
            📊 Статус: $statusRu
            📅 Создана: ${campaign.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}$lastSent

            $scheduleInfo

            ────────────────────
            📝 Текст сообщения:
            ────────────────────
            ${campaign.messageText}
            ────────────────────

            Выберите действие:
            ⏰ Настроить расписание
            🚀 Отправить сейчас
            🗑 Удалить кампанию
            ◀️ Назад
        """.cleanMessage()
    }

    /**
     * Обработка действий с деталями кампании
     */
    private fun handleCampaignDetailsAction(message: IncomingMessage): String {
        val campaignId = stateManager.getContextValue<Long>(message.userId, "selectedCampaignId")
            ?: return "❌ Кампания не выбрана. Попробуйте снова."

        return when (message.text) {
            "⏰ Настроить расписание" -> handleScheduleSetupStart(message, campaignId)
            "🚀 Отправить сейчас" -> handleSendCampaignNow(message, campaignId)
            "🗑 Удалить кампанию" -> handleDeleteCampaign(message, campaignId)
            "◀️ Назад" -> {
                stateManager.clearContext(message.userId)
                handleCampaignsList(message)
            }
            else -> "❓ Неизвестное действие. Используйте кнопки меню."
        }
    }

    /**
     * Начало настройки расписания
     */
    private fun handleScheduleSetupStart(message: IncomingMessage, campaignId: Long): String {
        stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TYPE)

        return """
            ⏰ Настройка расписания отправки

            Выберите тип расписания:

            1️⃣ Каждый час - отправка в заданную минуту каждого часа
            2️⃣ Каждый день - отправка в заданное время каждый день
            3️⃣ Каждые N часов - отправка с заданным интервалом
            4️⃣ Выключить расписание - только ручная отправка
            5️⃣ Каждые 15 минут - быстрая периодическая отправка

            Отправьте номер (1-5) или нажмите «❌ Отмена»
        """.cleanMessage()
    }

    /**
     * Обработка выбора типа расписания
     */
    private fun handleCampaignScheduleTypeInput(message: IncomingMessage): String {
        if (message.text == "❌ Отмена") {
            val campaignId = stateManager.getContextValue<Long>(message.userId, "selectedCampaignId")
                ?: return handleCampaignsList(message)
            return handleCampaignSelectById(IncomingMessage(
                message.chatId, message.userId, campaignId.toString(),
                message.firstName, message.lastName, message.username
            ))
        }

        val campaignId = stateManager.getContextValue<Long>(message.userId, "selectedCampaignId")
            ?: return "❌ Кампания не выбрана. Попробуйте снова."

        val campaign = broadcastDbService.findCampaignById(campaignId)
            ?: return "❌ Кампания не найдена"

        return when (message.text) {
            "1", "1️⃣" -> {
                // Каждый час
                stateManager.setContextValue(message.userId, "scheduleType", "HOURLY")
                stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TIME)
                """
                    ⏰ Настройка почасового расписания

                    Отправьте минуту часа для отправки (0-59)
                    Например: 0 - в начале каждого часа (12:00, 13:00...)
                              30 - в половине каждого часа (12:30, 13:30...)

                    Или нажмите «❌ Отмена»
                """.cleanMessage()
            }
            "2", "2️⃣" -> {
                // Каждый день
                stateManager.setContextValue(message.userId, "scheduleType", "DAILY")
                stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TIME)
                """
                    ⏰ Настройка ежедневного расписания

                    Отправьте час для отправки (0-23)
                    Например: 9 - каждый день в 9:00
                              18 - каждый день в 18:00

                    Или нажмите «❌ Отмена»
                """.cleanMessage()
            }
            "3", "3️⃣" -> {
                // Каждые N часов
                stateManager.setContextValue(message.userId, "scheduleType", "CUSTOM")
                stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_SCHEDULE_TIME)
                """
                    ⏰ Настройка интервального расписания

                    Отправьте интервал в часах (1-168)
                    Например: 3 - каждые 3 часа
                              6 - каждые 6 часов
                              24 - каждые 24 часа (раз в сутки)

                    Или нажмите «❌ Отмена»
                """.cleanMessage()
            }
            "4", "4️⃣" -> {
                // Выключить расписание
                campaign.scheduleEnabled = false
                campaign.nextSendAt = null
                broadcastDbService.updateCampaign(campaign)

                """
                    ✅ Расписание выключено

                    Кампания будет отправляться только по кнопке «🚀 Отправить сейчас»
                """.cleanMessage().also {
                    // Возвращаемся к деталям кампании
                    stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_VIEW_DETAILS)
                }
            }
            "5", "5️⃣" -> {
                // Каждые 15 минут
                campaign.scheduleType = "EVERY_15_MINUTES"
                campaign.scheduleIntervalHours = 15 // Сохраняем как минуты
                campaign.scheduleEnabled = true

                // Вычисляем следующую отправку (через 15 минут от текущего времени)
                val nextSend = OffsetDateTime.now().plusMinutes(15)
                campaign.nextSendAt = nextSend

                broadcastDbService.updateCampaign(campaign)

                """
                    ✅ Расписание настроено

                    Тип: Каждые 15 минут
                    Следующая отправка: ${nextSend.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}

                    Рассылка будет автоматически повторяться каждые 15 минут
                """.cleanMessage().also {
                    stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_VIEW_DETAILS)
                }
            }
            else -> "❌ Неверный выбор. Отправьте номер от 1 до 5."
        }
    }

    /**
     * Обработка ввода времени для расписания
     */
    private fun handleCampaignScheduleTimeInput(message: IncomingMessage): String {
        if (message.text == "❌ Отмена") {
            val campaignId = stateManager.getContextValue<Long>(message.userId, "selectedCampaignId")
                ?: return handleCampaignsList(message)
            return handleCampaignSelectById(IncomingMessage(
                message.chatId, message.userId, campaignId.toString(),
                message.firstName, message.lastName, message.username
            ))
        }

        val campaignId = stateManager.getContextValue<Long>(message.userId, "selectedCampaignId")
            ?: return "❌ Кампания не выбрана. Попробуйте снова."

        val scheduleType = stateManager.getContextValue<String>(message.userId, "scheduleType")
            ?: return "❌ Тип расписания не выбран. Попробуйте снова."

        val timeValue = message.text.toIntOrNull()
            ?: return "❌ Неверный формат. Отправьте число."

        val campaign = broadcastDbService.findCampaignById(campaignId)
            ?: return "❌ Кампания не найдена"

        // Валидация и настройка расписания в зависимости от типа
        val result = when (scheduleType) {
            "HOURLY" -> {
                if (timeValue !in 0..59) {
                    return "❌ Минута должна быть от 0 до 59. Попробуйте снова."
                }
                campaign.scheduleType = "HOURLY"
                campaign.scheduleIntervalHours = timeValue // Сохраняем минуту
                campaign.scheduleEnabled = true

                // Вычисляем следующую отправку
                val now = OffsetDateTime.now()
                var nextSend = now.withMinute(timeValue).withSecond(0).withNano(0)
                if (nextSend <= now) {
                    nextSend = nextSend.plusHours(1)
                }
                campaign.nextSendAt = nextSend

                "✅ Расписание настроено: каждый час в $timeValue минут\nСледующая отправка: ${nextSend.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
            }
            "DAILY" -> {
                if (timeValue !in 0..23) {
                    return "❌ Час должен быть от 0 до 23. Попробуйте снова."
                }
                campaign.scheduleType = "DAILY"
                campaign.scheduleIntervalHours = timeValue // Сохраняем час
                campaign.scheduleEnabled = true

                // Вычисляем следующую отправку
                val now = OffsetDateTime.now()
                var nextSend = now.withHour(timeValue).withMinute(0).withSecond(0).withNano(0)
                if (nextSend <= now) {
                    nextSend = nextSend.plusDays(1)
                }
                campaign.nextSendAt = nextSend

                "✅ Расписание настроено: каждый день в $timeValue:00\nСледующая отправка: ${nextSend.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
            }
            "CUSTOM" -> {
                if (timeValue !in 1..168) {
                    return "❌ Интервал должен быть от 1 до 168 часов. Попробуйте снова."
                }
                campaign.scheduleType = "CUSTOM"
                campaign.scheduleIntervalHours = timeValue
                campaign.scheduleEnabled = true

                // Вычисляем следующую отправку
                val nextSend = OffsetDateTime.now().plusHours(timeValue.toLong())
                campaign.nextSendAt = nextSend

                "✅ Расписание настроено: каждые $timeValue ч\nСледующая отправка: ${nextSend.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
            }
            else -> return "❌ Неизвестный тип расписания"
        }

        broadcastDbService.updateCampaign(campaign)

        // Возвращаемся к деталям кампании
        stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_VIEW_DETAILS)
        stateManager.clearContextValue(message.userId, "scheduleType")

        return result
    }

    /**
     * Отправка кампании сейчас
     */
    private fun handleSendCampaignNow(message: IncomingMessage, campaignId: Long): String {
        val campaign = broadcastDbService.findCampaignById(campaignId)
            ?: return "❌ Кампания не найдена"

        // Отправляем кампанию
        campaign.status = "SENDING"
        broadcastDbService.updateCampaign(campaign)

        try {
            val result = broadcastService.sendCampaign(campaign)

            // Обновляем статус
            campaign.status = if (result.failedSends == 0 && result.successfulSends > 0) {
                "SENT"
            } else if (result.successfulSends > 0) {
                "SENT"
            } else {
                "FAILED"
            }
            campaign.lastSentAt = OffsetDateTime.now()
            broadcastDbService.updateCampaign(campaign)

            // Формируем отчет
            val reportDetails = buildString {
                if (result.successfulSends > 0) {
                    appendLine("\n✅ Успешные отправки:")
                    result.results.filter { it.success }.forEach {
                        appendLine("   • ${it.channelName}")
                    }
                }

                if (result.failedSends > 0) {
                    appendLine("\n❌ Ошибки отправки:")
                    result.results.filter { !it.success }.forEach {
                        appendLine("   • ${it.channelName}")
                        appendLine("     Причина: ${it.error}")
                    }
                }
            }

            return """
                ${if (result.failedSends == 0) "✅" else "⚠️"} Отправка завершена!

                📊 Статистика:
                • Всего чатов: ${result.totalChannels}
                • Успешно: ${result.successfulSends}
                • Ошибок: ${result.failedSends}

                $reportDetails
            """.cleanMessage()

        } catch (e: Exception) {
            campaign.status = "FAILED"
            broadcastDbService.updateCampaign(campaign)
            return "❌ Ошибка при отправке кампании: ${e.message}"
        } finally {
            // Возвращаемся к деталям кампании
            stateManager.setState(message.userId, ConversationState.BROADCAST_CAMPAIGN_VIEW_DETAILS)
        }
    }

    /**
     * Удаление кампании
     */
    private fun handleDeleteCampaign(message: IncomingMessage, campaignId: Long): String {
        val campaign = broadcastDbService.findCampaignById(campaignId)
            ?: return "❌ Кампания не найдена"

        broadcastDbService.deleteCampaign(campaignId)

        stateManager.clearContext(message.userId)
        stateManager.setState(message.userId, ConversationState.BROADCAST_MENU)

        return """
            ✅ Кампания "${campaign.title}" удалена

            Нажмите "📋 Мои рассылки" для просмотра оставшихся кампаний.
        """.cleanMessage()
    }

    // ═══════════════════════════════════════════════════════════════
    // Экспорт откликов в Excel
    // ═══════════════════════════════════════════════════════════════

    /**
     * Экспорт откликов на вакансию в Excel файл
     */
    private fun handleExportApplicationsToExcel(message: IncomingMessage): String {
        try {
            val vacancyId = stateManager.getContextValue<Long>(message.userId, "viewingVacancyId")
                ?: return "❌ Вакансия не найдена. Вернитесь к списку откликов."

            val vacancy = vacancyDbService.findById(vacancyId)
                ?: return "❌ Вакансия не найдена"

            val applications = applicationDbService.findByVacancyId(vacancyId)

            if (applications.isEmpty()) {
                return """
                    ℹ️ Нет откликов для экспорта

                    На эту вакансию пока нет откликов.
                """.cleanMessage()
            }

            // Генерируем Excel файл
            val excelBytes = excelExportService.exportApplicationsToExcel(vacancy, applications)

            // Формируем имя файла
            val fileName = "Отклики_${vacancy.code}_${vacancy.title.take(30)}.xlsx"
                .replace(Regex("[^а-яА-Яa-zA-Z0-9_.-]"), "_")

            // Отправляем файл пользователю
            telegramApiClient.sendDocument(
                chatId = message.userId,
                fileBytes = excelBytes,
                fileName = fileName,
                caption = """
                    📊 Экспорт откликов на вакансию "${vacancy.title}"

                    Всего откликов: ${applications.size}
                    Дата экспорта: ${OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}
                """.trimIndent()
            )

            return """
                ✅ Файл отправлен!

                📥 Excel файл с ${applications.size} откликами успешно сформирован и отправлен.

                Файл содержит:
                • Информацию о вакансии
                • Полные данные всех откликов
                • Ответы на вопросы анкеты
                • Статусы и заметки

                Продолжайте просмотр откликов или нажмите "◀️ Назад"
            """.cleanMessage()

        } catch (e: Exception) {
            log.error("Error exporting applications to Excel", e)
            return """
                ❌ Ошибка при экспорте

                Не удалось создать Excel файл: ${e.message}

                Попробуйте позже или обратитесь в поддержку.
            """.cleanMessage()
        }
    }
}