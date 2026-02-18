package dev.weuizx.jobzi.telegram.pool.dto

import java.time.OffsetDateTime

/**
 * Type of authentication update
 */
enum class AuthUpdateType {
    /** Waiting for authentication code from Telegram */
    WAITING_CODE,

    /** Waiting for 2FA password */
    WAITING_PASSWORD,

    /** Authentication completed successfully */
    AUTHENTICATED,

    /** Authentication failed with error */
    ERROR,

    /** Account status changed */
    STATUS_CHANGED
}

/**
 * WebSocket message for authentication updates
 */
data class AuthUpdate(
    /** Account ID being authenticated */
    val accountId: Long,

    /** Type of update */
    val type: AuthUpdateType,

    /** Human-readable message */
    val message: String,

    /** Additional context (e.g., password hint) */
    val context: Map<String, String>? = null,

    /** Timestamp of the update */
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
