package dev.weuizx.jobzi.service

import dev.weuizx.jobzi.service.db.BroadcastDbService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Планировщик для периодической отправки рекламных сообщений
 */
@Service
class BroadcastScheduler(
    private val broadcastDbService: BroadcastDbService,
    private val broadcastService: BroadcastService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Проверяет и отправляет запланированные кампании
     * Выполняется каждую минуту
     */
    @Scheduled(fixedRate = 60000) // Каждую минуту
    fun sendScheduledCampaigns() {
        try {
            val campaigns = broadcastDbService.findCampaignsDueForSending()

            if (campaigns.isEmpty()) {
                return
            }

            log.info("Found ${campaigns.size} campaigns due for sending")

            campaigns.forEach { campaign ->
                try {
                    log.info("Sending scheduled campaign ${campaign.id}: ${campaign.title}")

                    // Обновляем статус на SENDING
                    campaign.status = "SENDING"
                    broadcastDbService.updateCampaign(campaign)

                    // Отправляем
                    val result = broadcastService.sendCampaign(campaign)

                    // Обновляем статус
                    campaign.status = if (result.failedSends == 0 && result.successfulSends > 0) {
                        "SENT"
                    } else if (result.successfulSends > 0) {
                        "SENT" // Частично отправлено
                    } else {
                        "FAILED"
                    }

                    // Обновляем время следующей отправки
                    broadcastDbService.updateNextSendTime(campaign)

                    log.info("Campaign ${campaign.id} sent: ${result.successfulSends}/${result.totalChannels} successful")

                } catch (e: Exception) {
                    log.error("Error sending scheduled campaign ${campaign.id}", e)
                    campaign.status = "FAILED"
                    broadcastDbService.updateCampaign(campaign)
                }
            }

        } catch (e: Exception) {
            log.error("Error in broadcast scheduler", e)
        }
    }
}