package dev.weuizx.jobzi.service.db

import dev.weuizx.jobzi.domain.BroadcastCampaign
import dev.weuizx.jobzi.domain.BroadcastChannel
import dev.weuizx.jobzi.repository.BroadcastCampaignRepository
import dev.weuizx.jobzi.repository.BroadcastChannelRepository
import dev.weuizx.jobzi.telegram.TelegramApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.time.OffsetDateTime

/**
 * Сервис для работы с рекламными рассылками
 */
@Service
@Transactional
class BroadcastDbService(
    private val channelRepository: BroadcastChannelRepository,
    private val campaignRepository: BroadcastCampaignRepository,
    private val telegramApiClient: TelegramApiClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ===== CHANNELS =====

    /**
     * Создает новый канал для бизнеса
     */
    fun createChannel(
        businessId: Long,
        channelId: String,
        channelName: String? = null
    ): BroadcastChannel {
        val channel = BroadcastChannel(
            businessId = businessId,
            channelId = channelId,
            channelName = channelName
        )
        return channelRepository.save(channel)
    }

    /**
     * Находит все каналы бизнеса
     */
    fun findChannelsByBusinessId(businessId: Long): List<BroadcastChannel> =
        channelRepository.findByBusinessId(businessId)

    /**
     * Находит активные каналы бизнеса
     */
    fun findActiveChannelsByBusinessId(businessId: Long): List<BroadcastChannel> =
        channelRepository.findByBusinessIdAndIsActive(businessId, true)

    /**
     * Находит канал по ID бизнеса и ID канала
     */
    fun findChannelByBusinessIdAndChannelId(businessId: Long, channelId: String): BroadcastChannel? =
        channelRepository.findByBusinessIdAndChannelId(businessId, channelId)

    /**
     * Проверяет, существует ли канал
     */
    fun channelExists(businessId: Long, channelId: String): Boolean =
        channelRepository.existsByBusinessIdAndChannelId(businessId, channelId)

    /**
     * Обновляет канал
     */
    fun updateChannel(channel: BroadcastChannel): BroadcastChannel {
        channel.updatedAt = OffsetDateTime.now()
        return channelRepository.save(channel)
    }

    /**
     * Удаляет канал
     */
    fun deleteChannel(channelId: Long) {
        channelRepository.deleteById(channelId)
    }

    /**
     * Проверяет, может ли бот отправлять сообщения в группу/чат
     * Возвращает ChannelValidationResult с результатом проверки
     */
    fun validateChannelAccess(channelId: String): ChannelValidationResult {
        return try {
            // Получаем информацию о чате
            val chat = telegramApiClient.getChat(channelId)

            // Получаем информацию о правах бота в чате
            val botUserId = telegramApiClient.getBotUserId()
            val chatMember = telegramApiClient.getChatMember(channelId, botUserId)

            // Проверяем, что бот является участником
            val isMember = chatMember.status == "member" ||
                          chatMember.status == "administrator" ||
                          chatMember.status == "creator"

            if (!isMember) {
                return ChannelValidationResult(
                    success = false,
                    channelName = chat.title,
                    error = "Бот не является участником группы"
                )
            }

            // Проверяем, что бот не ограничен в правах (не забанен, может писать)
            val isRestricted = chatMember.status == "restricted" || chatMember.status == "kicked"

            if (isRestricted) {
                return ChannelValidationResult(
                    success = false,
                    channelName = chat.title,
                    error = "Бот ограничен в правах или удален из группы"
                )
            }

            // Проверяем, может ли бот отправлять сообщения
            val canSendMessages = chatMember.status == "administrator" ||
                                 chatMember.status == "creator" ||
                                 chatMember.status == "member"

            if (!canSendMessages) {
                return ChannelValidationResult(
                    success = false,
                    channelName = chat.title,
                    error = "Бот не может отправлять сообщения в эту группу"
                )
            }

            ChannelValidationResult(
                success = true,
                channelName = chat.title,
                channelType = if (chat.userName != null) "PUBLIC" else "PRIVATE"
            )

        } catch (e: TelegramApiException) {
            log.error("Error validating chat access for $channelId", e)
            ChannelValidationResult(
                success = false,
                error = "Ошибка проверки чата: ${e.message ?: "Неизвестная ошибка"}"
            )
        } catch (e: Exception) {
            log.error("Unexpected error validating chat $channelId", e)
            ChannelValidationResult(
                success = false,
                error = "Неожиданная ошибка: ${e.message ?: "Неизвестная ошибка"}"
            )
        }
    }

    // ===== CAMPAIGNS =====

    /**
     * Создает новую рекламную кампанию
     */
    fun createCampaign(
        businessId: Long,
        title: String,
        messageText: String,
        createdByUserId: Long
    ): BroadcastCampaign {
        val campaign = BroadcastCampaign(
            businessId = businessId,
            title = title,
            messageText = messageText,
            createdByUserId = createdByUserId
        )
        return campaignRepository.save(campaign)
    }

    /**
     * Находит все кампании бизнеса
     */
    fun findCampaignsByBusinessId(businessId: Long): List<BroadcastCampaign> =
        campaignRepository.findByBusinessId(businessId)

    /**
     * Находит кампании бизнеса по статусу
     */
    fun findCampaignsByBusinessIdAndStatus(businessId: Long, status: String): List<BroadcastCampaign> =
        campaignRepository.findByBusinessIdAndStatus(businessId, status)

    /**
     * Находит кампанию по ID
     */
    fun findCampaignById(campaignId: Long): BroadcastCampaign? =
        campaignRepository.findById(campaignId).orElse(null)

    /**
     * Обновляет кампанию
     */
    fun updateCampaign(campaign: BroadcastCampaign): BroadcastCampaign {
        campaign.updatedAt = OffsetDateTime.now()
        return campaignRepository.save(campaign)
    }

    /**
     * Удаляет кампанию
     */
    fun deleteCampaign(campaignId: Long) {
        campaignRepository.deleteById(campaignId)
    }

    /**
     * Находит кампании, которые нужно отправить
     * (schedule_enabled = true AND next_send_at <= NOW)
     */
    fun findCampaignsDueForSending(): List<BroadcastCampaign> {
        val now = OffsetDateTime.now()
        return campaignRepository.findAll()
            .filter { it.scheduleEnabled && it.nextSendAt != null && it.nextSendAt!! <= now }
    }

    /**
     * Вычисляет и обновляет next_send_at на основе типа расписания
     */
    fun updateNextSendTime(campaign: BroadcastCampaign) {
        campaign.lastSentAt = OffsetDateTime.now()

        when (campaign.scheduleType) {
            "ONCE" -> {
                // Одноразовая отправка - отключаем расписание
                campaign.scheduleEnabled = false
                campaign.nextSendAt = null
            }
            "HOURLY" -> {
                // Почасовая отправка - в заданную минуту каждого часа
                val minute = campaign.scheduleIntervalHours ?: 0
                val now = OffsetDateTime.now()
                var nextSend = now.withMinute(minute).withSecond(0).withNano(0)

                // Если время в текущем часу уже прошло, переходим к следующему часу
                if (nextSend <= now) {
                    nextSend = nextSend.plusHours(1)
                }

                campaign.nextSendAt = nextSend
            }
            "DAILY" -> {
                // Ежедневная отправка - в заданный час каждый день
                val hour = campaign.scheduleIntervalHours ?: 0
                val now = OffsetDateTime.now()
                var nextSend = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)

                // Если время сегодня уже прошло, переходим к следующему дню
                if (nextSend <= now) {
                    nextSend = nextSend.plusDays(1)
                }

                campaign.nextSendAt = nextSend
            }
            "WEEKLY" -> {
                // Еженедельная отправка - следующая отправка через 7 дней
                campaign.nextSendAt = OffsetDateTime.now().plusDays(7)
            }
            "EVERY_15_MINUTES" -> {
                // Отправка каждые 15 минут
                campaign.nextSendAt = OffsetDateTime.now().plusMinutes(15)
            }
            "CUSTOM" -> {
                // Пользовательский интервал
                val hours = campaign.scheduleIntervalHours ?: 24
                campaign.nextSendAt = OffsetDateTime.now().plusHours(hours.toLong())
            }
            else -> {
                // Неизвестный тип - отключаем
                campaign.scheduleEnabled = false
                campaign.nextSendAt = null
            }
        }

        updateCampaign(campaign)
    }

    /**
     * Автоматически регистрирует чат/группу для бизнеса
     * Используется когда бот добавляется в чат
     * Не требует валидации - раз бот уже в чате, значит все ОК
     */
    fun autoRegisterChat(
        businessId: Long,
        chatId: String,
        chatTitle: String?,
        chatType: String?
    ): BroadcastChannel? {
        return try {
            // Проверяем, не добавлен ли уже этот чат
            if (channelExists(businessId, chatId)) {
                log.info("Chat $chatId already registered for business $businessId")
                return findChannelByBusinessIdAndChannelId(businessId, chatId)
            }

            // Создаем новый канал
            val channel = BroadcastChannel(
                businessId = businessId,
                channelId = chatId,
                channelName = chatTitle ?: "Чат без названия",
                channelType = chatType ?: "PRIVATE",
                isActive = true
            )

            val saved = channelRepository.save(channel)
            log.info("Auto-registered chat $chatId (${chatTitle ?: "untitled"}) for business $businessId")
            saved

        } catch (e: Exception) {
            log.error("Error auto-registering chat $chatId for business $businessId", e)
            null
        }
    }
}

/**
 * Результат валидации канала
 */
data class ChannelValidationResult(
    val success: Boolean,
    val channelName: String? = null,
    val channelType: String? = null,
    val error: String? = null
)