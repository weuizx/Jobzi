package dev.weuizx.jobzi.telegram

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
data class TelegramBotConfig(
    var token: String = "",

    var username: String = "",
)