package dev.weuizx.jobzi.telegram.handler

import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.service.ApplicantTelegramService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Хендлер для обработки команд соискателей
 */
@Component
class ApplicantHandler(
    private val applicantService: ApplicantTelegramService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(message: IncomingMessage): String {
        log.info("Applicant command received from ${message.userId}: ${message.text}")

        return when {
            message.text.startsWith("/start") -> applicantService.handleStart(message)
            // Передаем все остальные сообщения в сервис для обработки
            // (включая команды и коды вакансий)
            else -> applicantService.handleCommand(message, message.text)
        }
    }
}
