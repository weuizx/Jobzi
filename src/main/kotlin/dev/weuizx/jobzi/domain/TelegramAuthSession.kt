package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

enum class TelegramAuthSessionState {
    WAITING_CODE,
    WAITING_PASSWORD,
    COMPLETED,
    FAILED
}

@Entity
@Table(name = "telegram_auth_sessions")
data class TelegramAuthSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    val account: TelegramAccount,

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_state", nullable = false, length = 50)
    var authState: TelegramAuthSessionState,

    @Column(name = "code_hash", columnDefinition = "TEXT")
    var codeHash: String? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
