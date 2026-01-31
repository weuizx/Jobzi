package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "broadcast_channels",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_business_channel", columnNames = ["business_id", "channel_id"])
    ]
)
data class BroadcastChannel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "business_id", nullable = false)
    val businessId: Long,

    @Column(name = "channel_id", nullable = false, length = 100)
    var channelId: String,

    @Column(name = "channel_name", length = 200)
    var channelName: String? = null,

    @Column(name = "channel_type", nullable = false, length = 20)
    var channelType: String = "PUBLIC", // PUBLIC or PRIVATE

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "is_bot_admin", nullable = false)
    var isBotAdmin: Boolean = false,

    @Column(name = "last_validation_at")
    var lastValidationAt: OffsetDateTime? = null,

    @Column(name = "validation_error", columnDefinition = "TEXT")
    var validationError: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)