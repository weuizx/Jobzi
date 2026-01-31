package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.BroadcastCampaign
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BroadcastCampaignRepository : JpaRepository<BroadcastCampaign, Long> {
    fun findByBusinessId(businessId: Long): List<BroadcastCampaign>
    fun findByBusinessIdAndStatus(businessId: Long, status: String): List<BroadcastCampaign>
}