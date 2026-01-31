package dev.weuizx.jobzi.service.db

import dev.weuizx.jobzi.domain.Business
import dev.weuizx.jobzi.repository.BusinessRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class BusinessDbService(
    private val businessRepository: BusinessRepository
) {
    fun findById(id: Long): Business? = businessRepository.findById(id).orElse(null)

    fun findByTelegramChatId(telegramChatId: Long): Business? =
        businessRepository.findByTelegramChatId(telegramChatId)

    fun existsByTelegramChatId(telegramChatId: Long): Boolean =
        businessRepository.existsByTelegramChatId(telegramChatId)

    fun findAll(): List<Business> = businessRepository.findAll()

    fun findActive(): List<Business> = businessRepository.findByIsActive(true)

    fun save(business: Business): Business = businessRepository.save(business)

    fun createBusiness(
        name: String,
        telegramChatId: Long,
        description: String? = null
    ): Business {
        val business = Business(
            name = name,
            telegramChatId = telegramChatId,
            description = description
        )
        return businessRepository.save(business)
    }
}