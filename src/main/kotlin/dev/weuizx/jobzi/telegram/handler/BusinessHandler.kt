package dev.weuizx.jobzi.telegram.handler

import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.service.BusinessTelegramService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Хендлер для обработки команд представителей бизнеса
 */
@Component
class BusinessHandler(
    private val businessService: BusinessTelegramService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(message: IncomingMessage): String {
        log.info("Business command received from ${message.userId}: ${message.text}")

        return when {
            message.text.startsWith("/start") -> businessService.handleStart(message)
            // Передаем все остальные сообщения в сервис для обработки
            // (включая команды меню и ответы в процессе диалога)
            else -> businessService.handleCommand(message, message.text)
        }
    }
}
