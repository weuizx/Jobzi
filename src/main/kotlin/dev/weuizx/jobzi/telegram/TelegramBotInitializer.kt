package dev.weuizx.jobzi.telegram

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@Component
@ConditionalOnProperty(
    prefix = "telegram.bot",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true  // По умолчанию включен для обратной совместимости
)
class TelegramBotInitializer(
    private val jobziBot: JobziBot
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String) {
        try {
            log.info("Initializing Telegram Bot...")
            val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
            botsApi.registerBot(jobziBot)
            log.info("Telegram Bot registered successfully: ${jobziBot.botUsername}")
        } catch (e: Exception) {
            log.error("Failed to initialize Telegram Bot", e)
            throw e
        }
    }
}
