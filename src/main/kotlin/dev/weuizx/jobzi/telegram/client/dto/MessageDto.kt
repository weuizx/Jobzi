package dev.weuizx.jobzi.telegram.client.dto

data class SendMessageRequest(
    val chatId: Long,  // ID группы или пользователя
    val message: String
)

data class SendMessageByUsernameRequest(
    val username: String,  // Username публичного чата (например: "chatname" или "@chatname")
    val message: String
)

data class SendMessageResponse(
    val success: Boolean,
    val message: String,
    val messageId: Long? = null,
    val chatId: Long? = null  // ID чата (полезно при отправке по username)
)
