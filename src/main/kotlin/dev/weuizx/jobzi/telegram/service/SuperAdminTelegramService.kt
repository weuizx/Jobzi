package dev.weuizx.jobzi.telegram.service

import dev.weuizx.jobzi.service.business.ActivationResult
import dev.weuizx.jobzi.service.business.SuperAdminService
import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.pool.TelegramClientPoolManager
import dev.weuizx.jobzi.telegram.state.ConversationState
import dev.weuizx.jobzi.telegram.state.ConversationStateManager
import dev.weuizx.jobzi.service.db.TelegramAccountPoolDbService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

/**
 * Сервис для обработки команд суперадминистратора
 */
@Service
class SuperAdminTelegramService(
    private val superAdminService: SuperAdminService,
    private val stateManager: ConversationStateManager,
    private val poolManager: TelegramClientPoolManager,
    private val poolDbService: TelegramAccountPoolDbService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleStart(message: IncomingMessage): String {
        stateManager.clearState(message.userId)
        return """
            👑 Добро пожаловать, суперадмин!

            Используйте кнопки меню для управления системой.
        """.trimIndent()
    }

    /**
     * Обрабатывает команды из кнопочного меню
     */
    fun handleCommand(message: IncomingMessage, command: String): String {
        // Проверка прав суперадмина
        if (!superAdminService.isSuperAdmin(message.userId)) {
            return "⛔ У вас нет прав для выполнения этой команды"
        }

        // Проверяем, находится ли пользователь в диалоге
        val currentState = stateManager.getState(message.userId)
        if (currentState != ConversationState.NONE) {
            return handleConversationState(message)
        }

        // Обработка отмены
        if (command == "❌ Отмена") {
            stateManager.clearState(message.userId)
            return "❌ Действие отменено.\n\nВы вернулись в главное меню."
        }

        // Обработка обычных команд меню
        return when (command) {
            "📋 Список бизнесов" -> handleList()
            "➕ Активировать бизнес" -> handleActivateStart(message)
            "🔒 Управление доступом" -> handleAccessManagementMenu()
            "📊 Статистика" -> handleStatistics()
            "❓ Помощь" -> handleHelp()
            "🚫 Заблокировать бизнес" -> handleBlockStart(message)
            "✅ Разблокировать бизнес" -> handleUnblockStart(message)
            "◀️ Назад в меню" -> handleBackToMenu(message)
            // Telegram Pool команды
            "📱 Telegram аккаунты" -> handleTelegramPoolMenu()
            "📋 Список аккаунтов" -> handleListAccounts()
            "📊 Статус пула" -> handlePoolStatus()
            else -> "❓ Неизвестная команда. Используйте кнопки меню для навигации."
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
            ConversationState.SUPERADMIN_ACTIVATE_ENTER_TELEGRAM_ID -> handleActivateTelegramIdInput(message)
            ConversationState.SUPERADMIN_ACTIVATE_ENTER_NAME -> handleActivateNameInput(message)
            ConversationState.SUPERADMIN_ACTIVATE_ENTER_DESCRIPTION -> handleActivateDescriptionInput(message)
            ConversationState.SUPERADMIN_BLOCK_ENTER_ID -> handleBlockIdInput(message)
            ConversationState.SUPERADMIN_BLOCK_ENTER_REASON -> handleBlockReasonInput(message)
            ConversationState.SUPERADMIN_UNBLOCK_ENTER_ID -> handleUnblockIdInput(message)
            else -> {
                stateManager.clearState(message.userId)
                "❌ Произошла ошибка. Попробуйте снова."
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Активация бизнеса (пошаговый диалог)
    // ═══════════════════════════════════════════════════════════════

    private fun handleActivateStart(message: IncomingMessage): String {
        stateManager.setState(message.userId, ConversationState.SUPERADMIN_ACTIVATE_ENTER_TELEGRAM_ID)

        return """
            ➕ Активация нового бизнеса (Шаг 1 из 3)

            Введите Telegram ID владельца бизнеса:

            💡 Подсказка: Владелец может узнать свой ID через бота @userinfobot
        """.trimIndent()
    }

    private fun handleActivateTelegramIdInput(message: IncomingMessage): String {
        val telegramId = message.text.trim().toLongOrNull()

        if (telegramId == null) {
            return """
                ❌ Неверный формат Telegram ID.

                Telegram ID должен быть числом (например: 123456789).

                Введите корректный Telegram ID:
            """.trimIndent()
        }

        // Сохраняем Telegram ID в контексте
        stateManager.setContextValue(message.userId, "activateTelegramId", telegramId)
        stateManager.setState(message.userId, ConversationState.SUPERADMIN_ACTIVATE_ENTER_NAME)

        return """
            ✅ Telegram ID сохранен: $telegramId

            Шаг 2 из 3: Название бизнеса

            Введите название бизнеса (например: "Строительная бригада Мастер"):
        """.trimIndent()
    }

    private fun handleActivateNameInput(message: IncomingMessage): String {
        val businessName = message.text.trim()

        if (businessName.length < 3) {
            return "❌ Название слишком короткое. Минимум 3 символа.\n\nВведите название бизнеса:"
        }

        if (businessName.length > 255) {
            return "❌ Название слишком длинное. Максимум 255 символов.\n\nВведите название бизнеса:"
        }

        // Сохраняем название
        stateManager.setContextValue(message.userId, "activateBusinessName", businessName)
        stateManager.setState(message.userId, ConversationState.SUPERADMIN_ACTIVATE_ENTER_DESCRIPTION)

        return """
            ✅ Название сохранено: "$businessName"

            Шаг 3 из 3: Описание бизнеса (необязательно)

            Введите краткое описание бизнеса или отправьте "-" чтобы пропустить:
        """.trimIndent()
    }

    private fun handleActivateDescriptionInput(message: IncomingMessage): String {
        val description = message.text.trim()
        val descriptionValue = if (description == "-") null else description

        // Получаем сохраненные данные
        val telegramId = stateManager.getContextValue<Long>(message.userId, "activateTelegramId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: Telegram ID не найден. Начните заново."
        }

        val businessName = stateManager.getContextValue<String>(message.userId, "activateBusinessName") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: Название бизнеса не найдено. Начните заново."
        }

        // Активируем бизнес
        val result = superAdminService.activateBusiness(
            ownerTelegramId = telegramId,
            businessName = businessName,
            description = descriptionValue
        )

        // Очищаем состояние
        stateManager.clearState(message.userId)

        return when (result) {
            is ActivationResult.Success -> """
                ✅ Бизнес успешно активирован!

                🏢 Название: ${result.businessName}
                🆔 ID бизнеса: ${result.businessId}
                👤 Telegram ID владельца: $telegramId
                ${if (descriptionValue != null) "📝 Описание: $descriptionValue" else ""}

                ────────────────────

                ✉️ Попросите пользователя написать боту /start для входа в систему
            """.trimIndent()

            is ActivationResult.BusinessAlreadyExists -> """
                ⚠️ Бизнес с таким Telegram ID уже существует

                Используйте "📋 Список бизнесов" для просмотра всех бизнесов
            """.trimIndent()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Блокировка бизнеса (пошаговый диалог)
    // ═══════════════════════════════════════════════════════════════

    private fun handleBlockStart(message: IncomingMessage): String {
        // Показываем список бизнесов для удобства
        val businessList = handleList()

        stateManager.setState(message.userId, ConversationState.SUPERADMIN_BLOCK_ENTER_ID)

        return """
            $businessList

            ────────────────────

            🚫 Блокировка бизнеса (Шаг 1 из 2)

            Введите ID бизнеса для блокировки:
        """.trimIndent()
    }

    private fun handleBlockIdInput(message: IncomingMessage): String {
        val businessId = message.text.trim().toLongOrNull()

        if (businessId == null) {
            return """
                ❌ Неверный формат ID.

                ID должен быть числом (например: 5).

                Введите корректный ID бизнеса:
            """.trimIndent()
        }

        // Проверяем, существует ли бизнес
        val businesses = superAdminService.listAllBusinesses()
        val business = businesses.find { it.id == businessId }

        if (business == null) {
            return """
                ❌ Бизнес с ID $businessId не найден.

                Используйте "📋 Список бизнесов" для просмотра доступных ID.

                Введите корректный ID:
            """.trimIndent()
        }

        // Сохраняем ID
        stateManager.setContextValue(message.userId, "blockBusinessId", businessId)
        stateManager.setContextValue(message.userId, "blockBusinessName", business.name)
        stateManager.setState(message.userId, ConversationState.SUPERADMIN_BLOCK_ENTER_REASON)

        return """
            ✅ Выбран бизнес: "${business.name}" (ID: $businessId)

            Шаг 2 из 2: Причина блокировки

            Введите причину блокировки (например: "Нарушение правил", "Неуплата" и т.д.):
        """.trimIndent()
    }

    private fun handleBlockReasonInput(message: IncomingMessage): String {
        val reason = message.text.trim()

        if (reason.length < 3) {
            return "❌ Причина слишком короткая. Минимум 3 символа.\n\nВведите причину блокировки:"
        }

        // Получаем сохраненные данные
        val businessId = stateManager.getContextValue<Long>(message.userId, "blockBusinessId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: ID бизнеса не найден. Начните заново."
        }

        val businessName = stateManager.getContextValue<String>(message.userId, "blockBusinessName") ?: "???"

        // Блокируем бизнес
        val success = superAdminService.blockBusiness(businessId, reason)

        // Очищаем состояние
        stateManager.clearState(message.userId)

        return if (success) {
            """
                🚫 Бизнес заблокирован

                🆔 ID: $businessId
                🏢 Название: $businessName
                📝 Причина: $reason
                📅 Дата блокировки: ${java.time.OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}

                ────────────────────

                Владелец бизнеса больше не сможет управлять вакансиями до разблокировки.
            """.trimIndent()
        } else {
            "❌ Ошибка при блокировке бизнеса с ID $businessId"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Разблокировка бизнеса (пошаговый диалог)
    // ═══════════════════════════════════════════════════════════════

    private fun handleUnblockStart(message: IncomingMessage): String {
        // Показываем список заблокированных бизнесов
        val businesses = superAdminService.listAllBusinesses()
        val blockedBusinesses = businesses.filter { !it.isActive }

        if (blockedBusinesses.isEmpty()) {
            return """
                ℹ️ Нет заблокированных бизнесов

                Все бизнесы в системе активны.
            """.trimIndent()
        }

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val blockedList = buildString {
            appendLine("🔴 Заблокированные бизнесы (${blockedBusinesses.size}):")
            appendLine()
            blockedBusinesses.forEachIndexed { index, business ->
                appendLine("${index + 1}. ID: ${business.id}")
                appendLine("   🏢 ${business.name}")
                appendLine("   👤 @${business.ownerUsername ?: "unknown"}")
                appendLine("   📅 ${business.createdAt.format(formatter)}")
                appendLine()
            }
        }

        stateManager.setState(message.userId, ConversationState.SUPERADMIN_UNBLOCK_ENTER_ID)

        return """
            $blockedList
            ────────────────────

            ✅ Разблокировка бизнеса

            Введите ID бизнеса для разблокировки:
        """.trimIndent()
    }

    private fun handleUnblockIdInput(message: IncomingMessage): String {
        val businessId = message.text.trim().toLongOrNull()

        if (businessId == null) {
            return """
                ❌ Неверный формат ID.

                ID должен быть числом (например: 5).

                Введите корректный ID бизнеса:
            """.trimIndent()
        }

        // Разблокируем бизнес
        val success = superAdminService.unblockBusiness(businessId)

        // Очищаем состояние
        stateManager.clearState(message.userId)

        return if (success) {
            """
                ✅ Бизнес разблокирован

                🆔 ID: $businessId
                📅 Дата разблокировки: ${java.time.OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}

                ────────────────────

                Владелец бизнеса снова может управлять вакансиями.
            """.trimIndent()
        } else {
            "❌ Бизнес с ID $businessId не найден"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Остальные команды меню
    // ═══════════════════════════════════════════════════════════════

    private fun handleAccessManagementMenu(): String {
        return """
            🔒 Управление доступом

            Выберите действие с помощью кнопок ниже:
            • 🚫 Заблокировать бизнес - заблокировать доступ
            • ✅ Разблокировать бизнес - восстановить доступ
        """.trimIndent()
    }

    private fun handleList(): String {
        val businesses = superAdminService.listAllBusinesses()

        if (businesses.isEmpty()) {
            return "📋 Список бизнесов пуст"
        }

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        return buildString {
            appendLine("📋 Все бизнесы (${businesses.size}):")
            appendLine()

            businesses.forEachIndexed { index, business ->
                val status = if (business.isActive) "🟢 АКТИВЕН" else "🔴 ЗАБЛОКИРОВАН"
                appendLine("${index + 1}. $status • ID: ${business.id}")
                appendLine("   🏢 ${business.name}")
                appendLine("   👤 @${business.ownerUsername ?: "unknown"} (${business.ownerTelegramId ?: "N/A"})")
                appendLine("   📅 ${business.createdAt.format(formatter)}")
                appendLine()
            }
        }
    }

    private fun handleStatistics(): String {
        val businesses = superAdminService.listAllBusinesses()
        val activeCount = businesses.count { it.isActive }
        val blockedCount = businesses.count { !it.isActive }

        return """
            📊 Статистика системы

            🏢 Всего бизнесов: ${businesses.size}
            🟢 Активных: $activeCount
            🔴 Заблокированных: $blockedCount
        """.trimIndent()
    }

    private fun handleHelp(): String {
        return """
            ❓ Справка по использованию

            Доступные разделы:

            📋 Список бизнесов
            Просмотр всех зарегистрированных бизнесов с их статусами.

            ➕ Активировать бизнес
            Добавление нового бизнеса в систему. Бот попросит:
            1. Telegram ID владельца
            2. Название бизнеса
            3. Описание (необязательно)

            🔒 Управление доступом
            Блокировка и разблокировка бизнесов:
            • Блокировка - выбираете бизнес и указываете причину
            • Разблокировка - выбираете бизнес из заблокированных

            📊 Статистика
            Общая информация о системе (количество активных и заблокированных бизнесов).

            ────────────────────

            💡 Все действия выполняются через диалог с ботом - просто нажимайте кнопки и следуйте инструкциям!
        """.trimIndent()
    }

    private fun handleBackToMenu(message: IncomingMessage): String {
        stateManager.clearState(message.userId)
        return """
            👑 Главное меню суперадмина

            Выберите действие с помощью кнопок ниже.
        """.trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════
    // Управление Telegram пулом
    // ═══════════════════════════════════════════════════════════════

    private fun handleTelegramPoolMenu(): String {
        return """
            📱 Управление Telegram аккаунтами

            Управление пулом аккаунтов для отправки сообщений.

            Используйте кнопки ниже для управления:
        """.trimIndent()
    }

    private fun handleListAccounts(): String {
        try {
            val accounts = poolDbService.findAllAccounts()

            if (accounts.isEmpty()) {
                return """
                    📋 Список аккаунтов пуст

                    Нажмите "➕ Добавить аккаунт" для добавления нового.
                """.trimIndent()
            }

            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            return buildString {
                appendLine("📋 Telegram аккаунты (${accounts.size}):")
                appendLine()

                accounts.forEachIndexed { index, account ->
                    val statusEmoji = when (account.status) {
                        dev.weuizx.jobzi.domain.TelegramAccountStatus.AUTHENTICATED -> "🟢"
                        dev.weuizx.jobzi.domain.TelegramAccountStatus.AUTHENTICATING -> "🟡"
                        dev.weuizx.jobzi.domain.TelegramAccountStatus.INACTIVE -> "⚪"
                        dev.weuizx.jobzi.domain.TelegramAccountStatus.ERROR -> "🔴"
                    }

                    val phoneNumber = try {
                        poolDbService.decryptPhoneNumber(account)
                    } catch (e: Exception) {
                        "***"
                    }

                    appendLine("${index + 1}. $statusEmoji ${account.status}")
                    appendLine("   📱 Телефон: $phoneNumber")
                    appendLine("   🔑 Session: ${account.sessionName}")
                    appendLine("   📅 Создан: ${account.createdAt.format(formatter)}")

                    if (account.lastUsedAt != null) {
                        appendLine("   ⏰ Использован: ${account.lastUsedAt!!.format(formatter)}")
                    }

                    if (account.status == dev.weuizx.jobzi.domain.TelegramAccountStatus.ERROR && account.errorMessage != null) {
                        appendLine("   ⚠️ Ошибка: ${account.errorMessage}")
                    }

                    appendLine()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to list accounts", e)
            return "❌ Ошибка при получении списка аккаунтов: ${e.message}"
        }
    }

    private fun handlePoolStatus(): String {
        try {
            val status = poolManager.getPoolStatus()

            return """
                📊 Статус пула Telegram аккаунтов

                Всего клиентов: ${status["totalClients"]}
                🟢 Аутентифицированных: ${status["authenticatedClients"]}
                🟡 В процессе аутентификации: ${status["authenticatingClients"]}

                ────────────────────

                ℹ️ Только аутентифицированные аккаунты могут отправлять сообщения.
            """.trimIndent()
        } catch (e: Exception) {
            log.error("Failed to get pool status", e)
            return "❌ Ошибка при получении статуса пула: ${e.message}"
        }
    }
}
