package dev.weuizx.jobzi.telegram.handler

import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.service.SuperAdminTelegramService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Хендлер для обработки команд суперадминистратора
 */
@Component
class SuperAdminHandler(
    private val superAdminService: SuperAdminTelegramService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(message: IncomingMessage): String {
        log.info("SuperAdmin command received from ${message.userId}: ${message.text}")

        return when {
            message.text.startsWith("/start") -> superAdminService.handleStart(message)
            // Передаем все остальные сообщения в сервис для обработки
            // (включая команды меню и ответы в процессе диалога)
            else -> superAdminService.handleCommand(message, message.text)
        }
    }
}
