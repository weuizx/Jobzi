package dev.weuizx.jobzi.service

import dev.weuizx.jobzi.domain.BroadcastCampaign
import dev.weuizx.jobzi.domain.BroadcastChannel
import dev.weuizx.jobzi.service.db.BroadcastDbService
import dev.weuizx.jobzi.telegram.TelegramApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

/**
 * Сервис для отправки рекламных сообщений в каналы
 */
@Service
class BroadcastService(
    private val broadcastDbService: BroadcastDbService,
    private val telegramApiClient: TelegramApiClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Результат отправки сообщения в один канал
     */
    data class ChannelBroadcastResult(
        val channelId: String,
        val channelName: String?,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Результат рассылки кампании
     */
    data class BroadcastResult(
        val campaignId: Long,
        val totalChannels: Int,
        val successfulSends: Int,
        val failedSends: Int,
        val results: List<ChannelBroadcastResult>
    )

    /**
     * Отправляет рекламную кампанию во все активные каналы бизнеса
     */
    fun sendCampaign(campaign: BroadcastCampaign): BroadcastResult {
        log.info("Starting broadcast for campaign ${campaign.id}: ${campaign.title}")

        // Получаем активные каналы
        val channels = broadcastDbService.findActiveChannelsByBusinessId(campaign.businessId)

        if (channels.isEmpty()) {
            log.warn("No active channels found for business ${campaign.businessId}")
            return BroadcastResult(
                campaignId = campaign.id!!,
                totalChannels = 0,
                successfulSends = 0,
                failedSends = 0,
                results = emptyList()
            )
        }

        // Отправляем сообщение в каждый канал
        val results = channels.map { channel ->
            sendToChannel(channel, campaign.messageText)
        }

        val successCount = results.count { it.success }
        val failCount = results.count { !it.success }

        log.info("Campaign ${campaign.id} broadcast completed: $successCount successful, $failCount failed")

        return BroadcastResult(
            campaignId = campaign.id!!,
            totalChannels = channels.size,
            successfulSends = successCount,
            failedSends = failCount,
            results = results
        )
    }

    /**
     * Отправляет сообщение в один канал
     */
    private fun sendToChannel(channel: BroadcastChannel, messageText: String): ChannelBroadcastResult {
        return try {
            log.debug("Sending message to channel ${channel.channelId}")

            // Добавляем небольшую задержку между отправками для избежания rate limit
            Thread.sleep(100)

            // Отправляем сообщение
            telegramApiClient.sendMessageToChannel(channel.channelId, messageText)

            log.info("Successfully sent message to channel ${channel.channelId}")

            ChannelBroadcastResult(
                channelId = channel.channelId,
                channelName = channel.channelName,
                success = true
            )

        } catch (e: TelegramApiException) {
            log.error("Failed to send message to channel ${channel.channelId}: ${e.message}", e)

            // Обновляем информацию об ошибке в канале
            channel.validationError = "Ошибка отправки: ${e.message}"
            channel.isActive = false
            broadcastDbService.updateChannel(channel)

            ChannelBroadcastResult(
                channelId = channel.channelId,
                channelName = channel.channelName,
                success = false,
                error = e.message ?: "Неизвестная ошибка Telegram API"
            )

        } catch (e: Exception) {
            log.error("Unexpected error sending to channel ${channel.channelId}", e)

            ChannelBroadcastResult(
                channelId = channel.channelId,
                channelName = channel.channelName,
                success = false,
                error = "Неожиданная ошибка: ${e.message}"
            )
        }
    }
}