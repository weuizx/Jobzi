package dev.weuizx.jobzi.telegram.service

import dev.weuizx.jobzi.domain.Question
import dev.weuizx.jobzi.domain.QuestionType
import dev.weuizx.jobzi.domain.VacancyStatus
import dev.weuizx.jobzi.service.db.ApplicationDbService
import dev.weuizx.jobzi.service.db.QuestionDbService
import dev.weuizx.jobzi.service.db.UserDbService
import dev.weuizx.jobzi.service.db.VacancyDbService
import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.state.ConversationState
import dev.weuizx.jobzi.telegram.state.ConversationStateManager
import org.springframework.stereotype.Service

/**
 * Сервис для обработки команд соискателей
 */
@Service
class ApplicantTelegramService(
    private val userDbService: UserDbService,
    private val vacancyDbService: VacancyDbService,
    private val questionDbService: QuestionDbService,
    private val applicationDbService: ApplicationDbService,
    private val stateManager: ConversationStateManager
) {

    fun handleStart(message: IncomingMessage): String {
        // Создаем пользователя если его нет
        var user = userDbService.findByTelegramId(message.userId)
        if (user == null) {
            user = userDbService.createUser(
                telegramId = message.userId,
                firstName = message.firstName,
                lastName = message.lastName,
                username = message.username
            )
        }

        return """
            👋 Добро пожаловать в Jobzi!

            Это платформа для быстрого поиска работы.

            📋 Как откликнуться на вакансию:
            1. Получите код вакансии от работодателя
            2. Отправьте его мне
            3. Заполните короткую анкету
            4. Готово! Работодатель получит ваш отклик

            Отправьте код вакансии (например: ABC123) или команду:
            /help - помощь
        """.trimIndent()
    }

    fun handleCommand(message: IncomingMessage, command: String): String {
        // Проверяем состояние
        val currentState = stateManager.getState(message.userId)
        if (currentState != ConversationState.NONE) {
            return handleConversationState(message)
        }

        return when {
            command.startsWith("/help") -> handleHelp()
            command.startsWith("/my") -> handleMyApplications(message)
            // Все остальное считаем кодом вакансии
            else -> handleVacancyCodeInput(message, command)
        }
    }

    private fun handleConversationState(message: IncomingMessage): String {
        val state = stateManager.getState(message.userId)

        // Обработка отмены
        if (message.text == "❌ Отмена") {
            stateManager.clearState(message.userId)
            return "❌ Отклик отменен.\n\nОтправьте новый код вакансии для отклика."
        }

        return when (state) {
            ConversationState.APPLICANT_ANSWERING_QUESTION -> handleQuestionnaireAnswer(message)
            else -> {
                stateManager.clearState(message.userId)
                "❌ Произошла ошибка. Попробуйте снова."
            }
        }
    }

    private fun handleVacancyCodeInput(message: IncomingMessage, code: String): String {
        val cleanCode = code.trim().uppercase()

        // Проверяем формат кода (должен быть ABC123 - 3 буквы + 3 цифры)
        if (!cleanCode.matches(Regex("[A-Z]{3}\\d{3}"))) {
            return """
                ❓ Неверный формат кода вакансии.

                Код вакансии должен состоять из 6 символов (3 буквы + 3 цифры), например: ABC123

                Получите правильный код от работодателя и отправьте мне.
            """.trimIndent()
        }

        // Ищем вакансию
        val vacancy = vacancyDbService.findByCode(cleanCode)
        if (vacancy == null) {
            return """
                ❌ Вакансия с кодом $cleanCode не найдена.

                Возможно:
                • Код введен неправильно
                • Вакансия уже закрыта
                • Код устарел

                Проверьте код и попробуйте снова.
            """.trimIndent()
        }

        // Проверяем, что вакансия активна
        if (vacancy.status != VacancyStatus.ACTIVE) {
            return """
                ⚠️ Вакансия "$cleanCode" сейчас недоступна.

                ${if (vacancy.status == VacancyStatus.DRAFT) "Вакансия находится в черновике." else "Вакансия закрыта."}

                Попробуйте найти другую вакансию.
            """.trimIndent()
        }

        // Получаем или создаем пользователя
        var user = userDbService.findByTelegramId(message.userId)
        if (user == null) {
            user = userDbService.createUser(
                telegramId = message.userId,
                firstName = message.firstName,
                lastName = message.lastName,
                username = message.username
            )
        }

        // Проверяем, не откликался ли уже
        if (applicationDbService.existsByVacancyIdAndUserId(vacancy.id!!, user.id!!)) {
            return """
                ℹ️ Вы уже откликались на эту вакансию.

                📋 Вакансия: ${vacancy.title}

                Работодатель рассмотрит ваш отклик и свяжется с вами.

                Хотите откликнуться на другую вакансию? Отправьте новый код.
            """.trimIndent()
        }

        // Получаем вопросы анкеты
        val questions = questionDbService.findByVacancyId(vacancy.id)
        if (questions.isEmpty()) {
            // Если нет вопросов, сразу создаем отклик
            applicationDbService.createApplication(vacancy.id, user.id)

            return """
                ✅ Ваш отклик отправлен!

                📋 Вакансия: ${vacancy.title}
                🏢 Место: ${vacancy.location ?: "не указано"}
                💰 Зарплата: ${vacancy.salary ?: "не указана"}

                Работодатель получил ваш отклик и свяжется с вами в ближайшее время.

                Отправьте новый код для отклика на другую вакансию.
            """.trimIndent()
        }

        // Создаем отклик
        val application = applicationDbService.createApplication(vacancy.id, user.id)

        // Сохраняем контекст
        stateManager.setContextValue(message.userId, "applicationId", application.id!!)
        stateManager.setContextValue(message.userId, "vacancyTitle", vacancy.title)
        stateManager.setContextValue(message.userId, "vacancyCode", vacancy.code)
        stateManager.setContextValue(message.userId, "questions", questions)
        stateManager.setContextValue(message.userId, "currentQuestionIndex", 0)

        // Переходим к заполнению анкеты
        stateManager.setState(message.userId, ConversationState.APPLICANT_ANSWERING_QUESTION)

        // Показываем первый вопрос
        return buildQuestionMessage(questions, 0, vacancy.title)
    }

    private fun handleQuestionnaireAnswer(message: IncomingMessage): String {
        val applicationId = stateManager.getContextValue<Long>(message.userId, "applicationId") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: отклик не найден. Начните заново, отправив код вакансии."
        }

        val questions = stateManager.getContextValue<List<Question>>(message.userId, "questions") ?: run {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: вопросы не найдены."
        }

        val currentIndex = stateManager.getContextValue<Int>(message.userId, "currentQuestionIndex") ?: 0

        if (currentIndex >= questions.size) {
            stateManager.clearState(message.userId)
            return "❌ Ошибка: все вопросы уже заполнены."
        }

        val currentQuestion = questions[currentIndex]
        val answer = message.text.trim()

        // Валидация ответа
        val validationError = validateAnswer(currentQuestion, answer)
        if (validationError != null) {
            return validationError
        }

        // Сохраняем ответ
        applicationDbService.saveAnswer(
            applicationId = applicationId,
            questionId = currentQuestion.id!!,
            answerText = answer,
            questionText = currentQuestion.questionText,
            questionType = currentQuestion.questionType.name,
            questionOrder = currentIndex + 1
        )

        // Переходим к следующему вопросу
        val nextIndex = currentIndex + 1

        if (nextIndex >= questions.size) {
            // Все вопросы заполнены
            val vacancyTitle = stateManager.getContextValue<String>(message.userId, "vacancyTitle") ?: "вакансию"
            val vacancyCode = stateManager.getContextValue<String>(message.userId, "vacancyCode") ?: "???"

            stateManager.clearState(message.userId)

            return """
                ✅ Ваш отклик успешно отправлен!

                📋 Вакансия: $vacancyTitle
                🆔 Код: $vacancyCode
                ✏️ Анкета заполнена: ${questions.size} вопросов

                ────────────────────

                Работодатель получил ваш отклик и ответы на вопросы.
                Он свяжется с вами в ближайшее время!

                Хотите откликнуться на другую вакансию? Отправьте новый код.
            """.trimIndent()
        }

        // Сохраняем новый индекс
        stateManager.setContextValue(message.userId, "currentQuestionIndex", nextIndex)

        // Показываем следующий вопрос
        val vacancyTitle = stateManager.getContextValue<String>(message.userId, "vacancyTitle") ?: "вакансию"
        return buildQuestionMessage(questions, nextIndex, vacancyTitle)
    }

    private fun buildQuestionMessage(questions: List<Question>, index: Int, vacancyTitle: String): String {
        val question = questions[index]
        val progress = "${index + 1}/${questions.size}"
        val required = if (question.isRequired) "обязательный" else "необязательный"

        val typeHint = when (question.questionType) {
            QuestionType.TEXT -> "Введите текст ответа"
            QuestionType.NUMBER -> "Введите число"
            QuestionType.PHONE -> "Введите номер телефона"
            QuestionType.YES_NO -> "Ответьте: Да или Нет"
            QuestionType.CHOICE -> {
                val options = questionDbService.parseOptions(question.options)
                if (options != null) {
                    "Выберите один из вариантов:\n" + options.mapIndexed { i, opt -> "${i + 1}. $opt" }.joinToString("\n")
                } else {
                    "Введите ответ"
                }
            }
            QuestionType.DATE -> "Введите дату (например: 01.01.2000)"
        }

        return """
            📋 Отклик на вакансию: $vacancyTitle

            Вопрос $progress ($required):

            ❓ ${question.questionText}

            $typeHint

            ${if (!question.isRequired) "\nМожно пропустить, отправив: -" else ""}
        """.trimIndent()
    }

    private fun validateAnswer(question: Question, answer: String): String? {
        // Проверка на пропуск необязательного вопроса
        if (!question.isRequired && answer == "-") {
            return null // Ответ валиден
        }

        // Проверка на пустой ответ для обязательного вопроса
        if (question.isRequired && answer.isBlank()) {
            return "❌ Этот вопрос обязательный. Пожалуйста, введите ответ."
        }

        // Специфичная валидация по типу вопроса
        return when (question.questionType) {
            QuestionType.NUMBER -> {
                if (answer.toIntOrNull() == null && answer != "-") {
                    "❌ Ожидается число. Введите корректное значение."
                } else null
            }
            QuestionType.PHONE -> {
                if (!answer.matches(Regex("\\+?[0-9\\-\\(\\)\\s]{7,20}")) && answer != "-") {
                    "❌ Неверный формат телефона. Введите номер телефона."
                } else null
            }
            QuestionType.YES_NO -> {
                val normalized = answer.lowercase()
                if (!normalized.contains("да") && !normalized.contains("нет") &&
                    !normalized.contains("yes") && !normalized.contains("no") && answer != "-") {
                    "❌ Ответьте: Да или Нет"
                } else null
            }
            QuestionType.CHOICE -> {
                val options = questionDbService.parseOptions(question.options)
                if (options != null) {
                    val answerNum = answer.toIntOrNull()
                    if (answerNum != null && (answerNum < 1 || answerNum > options.size)) {
                        "❌ Выберите номер от 1 до ${options.size}"
                    } else if (answerNum == null && !options.any { it.equals(answer, ignoreCase = true) } && answer != "-") {
                        "❌ Неверный вариант. Выберите номер или введите текст варианта."
                    } else null
                } else null
            }
            else -> null // TEXT, DATE - без специфичной валидации
        }
    }

    private fun handleHelp(): String {
        return """
            ❓ Справка по использованию

            📋 Как откликнуться на вакансию:
            1. Получите код вакансии от работодателя (например: ABC123)
            2. Отправьте код мне
            3. Заполните короткую анкету
            4. Готово! Работодатель получит ваш отклик

            💡 Полезные команды:
            /start - начать сначала
            /my - мои отклики
            /help - эта справка

            При заполнении анкеты:
            • Отправьте "-" чтобы пропустить необязательный вопрос
            • Отправьте "❌ Отмена" чтобы отменить отклик
        """.trimIndent()
    }

    private fun handleMyApplications(message: IncomingMessage): String {
        val user = userDbService.findByTelegramId(message.userId)
            ?: return "❌ Вы еще не откликались на вакансии."

        val applications = applicationDbService.findByUserId(user.id!!)
        if (applications.isEmpty()) {
            return """
                📋 У вас пока нет откликов на вакансии.

                Отправьте код вакансии для отклика.
            """.trimIndent()
        }

        return buildString {
            appendLine("📋 Ваши отклики (${applications.size}):")
            appendLine()

            applications.forEachIndexed { index, app ->
                val vacancy = vacancyDbService.findById(app.vacancyId)
                val statusEmoji = when (app.status) {
                    dev.weuizx.jobzi.domain.ApplicationStatus.NEW -> "🆕"
                    dev.weuizx.jobzi.domain.ApplicationStatus.VIEWED -> "👀"
                    dev.weuizx.jobzi.domain.ApplicationStatus.CONTACTED -> "📞"
                    dev.weuizx.jobzi.domain.ApplicationStatus.ACCEPTED -> "✅"
                    dev.weuizx.jobzi.domain.ApplicationStatus.REJECTED -> "❌"
                }

                appendLine("${index + 1}. $statusEmoji ${app.status.name}")
                appendLine("   📋 ${vacancy?.title ?: "Неизвестная вакансия"}")
                appendLine("   🆔 Код: ${vacancy?.code ?: "???"}")
                appendLine("   📅 Отправлен: ${app.createdAt.toLocalDate()}")
                appendLine()
            }

            appendLine("────────────────────")
            appendLine("🆕 NEW - новый отклик")
            appendLine("👀 VIEWED - работодатель просмотрел")
            appendLine("📞 CONTACTED - работодатель связался")
            appendLine("✅ ACCEPTED - вас приняли!")
            appendLine("❌ REJECTED - отказ")
        }
    }
}