package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "telegram_accounts")
data class TelegramAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "phone_number_encrypted", nullable = false, columnDefinition = "TEXT")
    val phoneNumberEncrypted: String,

    @Column(name = "api_id", nullable = false)
    val apiId: Int,

    @Column(name = "api_hash_encrypted", nullable = false, columnDefinition = "TEXT")
    val apiHashEncrypted: String,

    @Column(name = "session_name", nullable = false, unique = true, length = 255)
    val sessionName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: TelegramAccountStatus = TelegramAccountStatus.INACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_state", length = 50)
    var authState: TelegramAuthState? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: OffsetDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    var createdByUser: User? = null
)
