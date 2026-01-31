package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "broadcast_campaigns")
data class BroadcastCampaign(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "business_id", nullable = false)
    val businessId: Long,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    var messageText: String,

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "DRAFT", // DRAFT, READY, SENDING, SENT, FAILED

    @Column(name = "created_by_user_id", nullable = false)
    val createdByUserId: Long,

    // Schedule fields
    @Column(name = "schedule_enabled", nullable = false)
    var scheduleEnabled: Boolean = false,

    @Column(name = "schedule_type", length = 20)
    var scheduleType: String = "ONCE", // ONCE, DAILY, WEEKLY, CUSTOM

    @Column(name = "schedule_interval_hours")
    var scheduleIntervalHours: Int? = null,

    @Column(name = "scheduled_at")
    var scheduledAt: OffsetDateTime? = null,

    @Column(name = "last_sent_at")
    var lastSentAt: OffsetDateTime? = null,

    @Column(name = "next_send_at")
    var nextSendAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)