package dev.weuizx.jobzi.telegram.dto

/**
 * Data Transfer Object для входящего Telegram сообщения
 */
data class IncomingMessage(
    val chatId: Long,
    val userId: Long,
    val text: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val username: String? = null
)