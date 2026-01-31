package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "businesses")
data class Business(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    val telegramChatId: Long,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)