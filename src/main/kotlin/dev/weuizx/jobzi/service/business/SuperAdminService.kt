package dev.weuizx.jobzi.service.business

import dev.weuizx.jobzi.domain.BusinessRole
import dev.weuizx.jobzi.service.db.BusinessDbService
import dev.weuizx.jobzi.service.db.BusinessUserDbService
import dev.weuizx.jobzi.service.db.UserDbService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SuperAdminService(
    private val userDbService: UserDbService,
    private val businessDbService: BusinessDbService,
    private val businessUserDbService: BusinessUserDbService
) {

    @Transactional
    fun activateBusiness(
        ownerTelegramId: Long,
        businessName: String,
        description: String? = null,
        ownerUsername: String? = null,
        ownerFirstName: String? = null,
        ownerLastName: String? = null
    ): ActivationResult {
        // Проверяем, существует ли бизнес с таким Telegram ID
        if (businessDbService.existsByTelegramChatId(ownerTelegramId)) {
            return ActivationResult.BusinessAlreadyExists
        }

        // Находим или создаем пользователя
        val user = userDbService.findByTelegramId(ownerTelegramId)
            ?: userDbService.createUser(
                telegramId = ownerTelegramId,
                username = ownerUsername,
                firstName = ownerFirstName,
                lastName = ownerLastName
            )

        // Создаем бизнес
        val business = businessDbService.createBusiness(
            name = businessName,
            telegramChatId = ownerTelegramId,
            description = description
        )

        // Связываем пользователя с бизнесом как ADMIN
        businessUserDbService.createBusinessUser(
            businessId = business.id!!,
            userId = user.id!!,
            role = BusinessRole.ADMIN
        )

        return ActivationResult.Success(
            businessId = business.id,
            userId = user.id,
            businessName = business.name
        )
    }

    fun listAllBusinesses(): List<BusinessInfo> {
        return businessDbService.findAll().map { business ->
            // Находим владельца (первый ADMIN)
            val admins = businessUserDbService.findByBusinessId(business.id!!)
                .filter { it.role == BusinessRole.ADMIN }

            val owner = admins.firstOrNull()?.let { businessUser ->
                userDbService.findById(businessUser.userId)
            }

            BusinessInfo(
                id = business.id,
                name = business.name,
                ownerUsername = owner?.username,
                ownerTelegramId = owner?.telegramId,
                isActive = business.isActive,
                createdAt = business.createdAt
            )
        }
    }

    @Transactional
    fun blockBusiness(businessId: Long, reason: String? = null): Boolean {
        val business = businessDbService.findById(businessId) ?: return false
        business.isActive = false
        business.updatedAt = java.time.OffsetDateTime.now()
        businessDbService.save(business)
        return true
    }

    @Transactional
    fun unblockBusiness(businessId: Long): Boolean {
        val business = businessDbService.findById(businessId) ?: return false
        business.isActive = true
        business.updatedAt = java.time.OffsetDateTime.now()
        businessDbService.save(business)
        return true
    }

    fun isSuperAdmin(telegramId: Long): Boolean {
        val user = userDbService.findByTelegramId(telegramId) ?: return false
        return user.role == dev.weuizx.jobzi.domain.UserRole.SUPERADMIN
    }
}

sealed class ActivationResult {
    data class Success(
        val businessId: Long,
        val userId: Long,
        val businessName: String
    ) : ActivationResult()

    object BusinessAlreadyExists : ActivationResult()
}

data class BusinessInfo(
    val id: Long,
    val name: String,
    val ownerUsername: String?,
    val ownerTelegramId: Long?,
    val isActive: Boolean,
    val createdAt: java.time.OffsetDateTime
)