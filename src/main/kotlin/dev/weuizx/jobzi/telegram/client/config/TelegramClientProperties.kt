package dev.weuizx.jobzi.telegram.client.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "telegram.client")
data class TelegramClientProperties(
    var apiId: Int = 0,
    var apiHash: String = "",
    var phoneNumber: String = "",
    var sessionPath: String = "./telegram-session",
    var enabled: Boolean = false
)
