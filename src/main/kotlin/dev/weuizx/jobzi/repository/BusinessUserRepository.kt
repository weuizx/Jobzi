package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.BusinessUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessUserRepository : JpaRepository<BusinessUser, Long> {
    fun findByBusinessIdAndUserId(businessId: Long, userId: Long): BusinessUser?
    fun findByUserId(userId: Long): List<BusinessUser>
    fun findByBusinessId(businessId: Long): List<BusinessUser>
    fun existsByUserId(userId: Long): Boolean
}