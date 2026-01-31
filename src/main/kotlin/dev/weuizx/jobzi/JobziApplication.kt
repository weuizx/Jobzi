package dev.weuizx.jobzi

import dev.weuizx.jobzi.telegram.TelegramBotConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotConfig::class)
@EnableScheduling
class JobziApplication

fun main(args: Array<String>) {
	runApplication<JobziApplication>(*args)
}
