package dev.weuizx.jobzi.service.db

import dev.weuizx.jobzi.domain.BusinessRole
import dev.weuizx.jobzi.domain.BusinessUser
import dev.weuizx.jobzi.repository.BusinessUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class BusinessUserDbService(
    private val businessUserRepository: BusinessUserRepository
) {
    fun findByBusinessIdAndUserId(businessId: Long, userId: Long): BusinessUser? =
        businessUserRepository.findByBusinessIdAndUserId(businessId, userId)

    fun findByUserId(userId: Long): List<BusinessUser> =
        businessUserRepository.findByUserId(userId)

    fun findByBusinessId(businessId: Long): List<BusinessUser> =
        businessUserRepository.findByBusinessId(businessId)

    fun save(businessUser: BusinessUser): BusinessUser = businessUserRepository.save(businessUser)

    fun createBusinessUser(
        businessId: Long,
        userId: Long,
        role: BusinessRole = BusinessRole.ADMIN
    ): BusinessUser {
        val businessUser = BusinessUser(
            businessId = businessId,
            userId = userId,
            role = role
        )
        return businessUserRepository.save(businessUser)
    }
}