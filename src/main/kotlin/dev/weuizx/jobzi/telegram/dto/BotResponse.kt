package dev.weuizx.jobzi.telegram.dto

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard

/**
 * DTO для ответа бота с опциональной клавиатурой
 */
data class BotResponse(
    val text: String,
    val keyboard: ReplyKeyboard? = null
)