package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.BroadcastChannel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BroadcastChannelRepository : JpaRepository<BroadcastChannel, Long> {
    fun findByBusinessId(businessId: Long): List<BroadcastChannel>
    fun findByBusinessIdAndIsActive(businessId: Long, isActive: Boolean): List<BroadcastChannel>
    fun findByBusinessIdAndChannelId(businessId: Long, channelId: String): BroadcastChannel?
    fun existsByBusinessIdAndChannelId(businessId: Long, channelId: String): Boolean
}