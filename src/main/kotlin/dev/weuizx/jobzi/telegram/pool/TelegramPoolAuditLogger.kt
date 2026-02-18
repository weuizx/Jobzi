package dev.weuizx.jobzi.telegram.pool

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Audit logger for Telegram pool operations.
 *
 * Creates a separate audit trail for security-sensitive operations
 * related to Telegram account management.
 */
@Component
class TelegramPoolAuditLogger {
    // Separate logger for audit trail
    private val auditLogger = LoggerFactory.getLogger("TELEGRAM_POOL_AUDIT")

    fun logAccountAdded(
        sessionName: String,
        phoneNumber: String,
        apiId: Int,
        createdByUserId: Long?
    ) {
        auditLogger.info(
            "ACCOUNT_ADDED | session=$sessionName | phone=$phoneNumber | apiId=$apiId | createdBy=$createdByUserId"
        )
    }

    fun logAccountDeleted(
        sessionName: String,
        accountId: Long,
        deletedByUserId: Long?
    ) {
        auditLogger.info(
            "ACCOUNT_DELETED | session=$sessionName | accountId=$accountId | deletedBy=$deletedByUserId"
        )
    }

    fun logAuthenticationStarted(
        sessionName: String,
        accountId: Long,
        initiatedByUserId: Long?
    ) {
        auditLogger.info(
            "AUTH_STARTED | session=$sessionName | accountId=$accountId | initiatedBy=$initiatedByUserId"
        )
    }

    fun logAuthenticationSuccess(
        sessionName: String,
        accountId: Long
    ) {
        auditLogger.info(
            "AUTH_SUCCESS | session=$sessionName | accountId=$accountId"
        )
    }

    fun logAuthenticationFailed(
        sessionName: String,
        accountId: Long,
        reason: String
    ) {
        auditLogger.warn(
            "AUTH_FAILED | session=$sessionName | accountId=$accountId | reason=$reason"
        )
    }

    fun logCodeSubmitted(
        sessionName: String,
        accountId: Long,
        submittedByUserId: Long?
    ) {
        auditLogger.info(
            "CODE_SUBMITTED | session=$sessionName | accountId=$accountId | submittedBy=$submittedByUserId"
        )
    }

    fun logPasswordSubmitted(
        sessionName: String,
        accountId: Long,
        submittedByUserId: Long?
    ) {
        auditLogger.info(
            "PASSWORD_SUBMITTED | session=$sessionName | accountId=$accountId | submittedBy=$submittedByUserId"
        )
    }

    fun logAccountStatusChanged(
        sessionName: String,
        accountId: Long,
        oldStatus: String,
        newStatus: String,
        reason: String? = null
    ) {
        val reasonPart = reason?.let { " | reason=$it" } ?: ""
        auditLogger.info(
            "STATUS_CHANGED | session=$sessionName | accountId=$accountId | oldStatus=$oldStatus | newStatus=$newStatus$reasonPart"
        )
    }

    fun logHealthCheckFailed(
        sessionName: String,
        accountId: Long,
        error: String
    ) {
        auditLogger.warn(
            "HEALTH_CHECK_FAILED | session=$sessionName | accountId=$accountId | error=$error"
        )
    }

    fun logMessageSent(
        sessionName: String,
        accountId: Long,
        chatId: Long,
        success: Boolean
    ) {
        auditLogger.info(
            "MESSAGE_SENT | session=$sessionName | accountId=$accountId | chatId=$chatId | success=$success"
        )
    }
}
