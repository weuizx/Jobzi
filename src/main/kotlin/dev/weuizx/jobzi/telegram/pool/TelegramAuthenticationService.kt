package dev.weuizx.jobzi.telegram.pool

import dev.weuizx.jobzi.domain.TelegramAuthSessionState
import dev.weuizx.jobzi.domain.TelegramAuthState
import dev.weuizx.jobzi.service.db.TelegramAccountPoolDbService
import dev.weuizx.jobzi.telegram.pool.dto.AuthUpdate
import dev.weuizx.jobzi.telegram.pool.dto.AuthUpdateType
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

/**
 * Service for coordinating Telegram account authentication flow.
 *
 * This service acts as a bridge between:
 * - TDLight auth requests (via DatabaseAuthenticationSupplier)
 * - Database state storage
 * - WebSocket notifications to admin
 * - REST API input from admin
 */
@Service
class TelegramAuthenticationService(
    private val dbService: TelegramAccountPoolDbService,
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Pending authentication requests: accountId -> CompletableFuture<code/password>
    private val pendingCodeRequests = ConcurrentHashMap<Long, CompletableFuture<String>>()
    private val pendingPasswordRequests = ConcurrentHashMap<Long, CompletableFuture<String>>()

    /**
     * Request authentication code from admin (called by TDLight)
     *
     * @return CompletableFuture that will be completed when admin submits code
     */
    fun requestCode(accountId: Long): CompletableFuture<String> {
        logger.info("Authentication code requested for account $accountId")

        // Create future for this request
        val codeFuture = CompletableFuture<String>()
        pendingCodeRequests[accountId] = codeFuture

        try {
            // Update database state
            dbService.updateAccountStatus(
                accountId = accountId,
                status = dev.weuizx.jobzi.domain.TelegramAccountStatus.AUTHENTICATING,
                authState = TelegramAuthState.WAITING_CODE
            )

            // Create auth session in database
            dbService.createAuthSession(
                accountId = accountId,
                authState = TelegramAuthSessionState.WAITING_CODE,
                expirationMinutes = 5
            )

            // Notify admin via WebSocket
            sendAuthUpdate(
                accountId = accountId,
                type = AuthUpdateType.WAITING_CODE,
                message = "Введите код подтверждения из Telegram"
            )

            logger.info("✓ Code request notification sent for account $accountId")

        } catch (e: Exception) {
            logger.error("Failed to request code for account $accountId", e)
            codeFuture.completeExceptionally(e)
            pendingCodeRequests.remove(accountId)
        }

        return codeFuture
    }

    /**
     * Submit authentication code from admin
     *
     * @return true if code was accepted, false if no pending request
     */
    fun submitCode(accountId: Long, code: String): Boolean {
        logger.info("Authentication code submitted for account $accountId")

        val codeFuture = pendingCodeRequests.remove(accountId)
        if (codeFuture == null) {
            logger.warn("No pending code request for account $accountId")
            return false
        }

        try {
            // Update auth session
            val session = dbService.findLatestAuthSession(accountId)
            if (session != null) {
                dbService.updateAuthSessionState(
                    sessionId = session.id!!,
                    authState = TelegramAuthSessionState.COMPLETED
                )
            }

            // Complete future to unblock TDLight
            codeFuture.complete(code)

            logger.info("✓ Code submitted successfully for account $accountId")
            return true

        } catch (e: Exception) {
            logger.error("Failed to submit code for account $accountId", e)
            codeFuture.completeExceptionally(e)
            return false
        }
    }

    /**
     * Request 2FA password from admin (called by TDLight)
     *
     * @param hint Password hint from Telegram
     * @return CompletableFuture that will be completed when admin submits password
     */
    fun requestPassword(accountId: Long, hint: String?): CompletableFuture<String> {
        logger.info("2FA password requested for account $accountId (hint: $hint)")

        // Create future for this request
        val passwordFuture = CompletableFuture<String>()
        pendingPasswordRequests[accountId] = passwordFuture

        try {
            // Update database state
            dbService.updateAccountStatus(
                accountId = accountId,
                status = dev.weuizx.jobzi.domain.TelegramAccountStatus.AUTHENTICATING,
                authState = TelegramAuthState.WAITING_PASSWORD
            )

            // Create auth session in database
            dbService.createAuthSession(
                accountId = accountId,
                authState = TelegramAuthSessionState.WAITING_PASSWORD,
                expirationMinutes = 5
            )

            // Notify admin via WebSocket
            val message = if (hint != null) {
                "Введите 2FA пароль (подсказка: $hint)"
            } else {
                "Введите 2FA пароль"
            }

            sendAuthUpdate(
                accountId = accountId,
                type = AuthUpdateType.WAITING_PASSWORD,
                message = message,
                context = if (hint != null) mapOf("hint" to hint) else null
            )

            logger.info("✓ Password request notification sent for account $accountId")

        } catch (e: Exception) {
            logger.error("Failed to request password for account $accountId", e)
            passwordFuture.completeExceptionally(e)
            pendingPasswordRequests.remove(accountId)
        }

        return passwordFuture
    }

    /**
     * Submit 2FA password from admin
     *
     * @return true if password was accepted, false if no pending request
     */
    fun submitPassword(accountId: Long, password: String): Boolean {
        logger.info("2FA password submitted for account $accountId")

        val passwordFuture = pendingPasswordRequests.remove(accountId)
        if (passwordFuture == null) {
            logger.warn("No pending password request for account $accountId")
            return false
        }

        try {
            // Update auth session
            val session = dbService.findLatestAuthSession(accountId)
            if (session != null) {
                dbService.updateAuthSessionState(
                    sessionId = session.id!!,
                    authState = TelegramAuthSessionState.COMPLETED
                )
            }

            // Complete future to unblock TDLight
            passwordFuture.complete(password)

            logger.info("✓ Password submitted successfully for account $accountId")
            return true

        } catch (e: Exception) {
            logger.error("Failed to submit password for account $accountId", e)
            passwordFuture.completeExceptionally(e)
            return false
        }
    }

    /**
     * Notify that authentication is complete (called by PoolManager)
     */
    fun notifyAuthenticationComplete(accountId: Long, success: Boolean, errorMessage: String? = null) {
        logger.info("Authentication completed for account $accountId: success=$success")

        // Cleanup pending requests
        pendingCodeRequests.remove(accountId)?.cancel(false)
        pendingPasswordRequests.remove(accountId)?.cancel(false)

        // Send WebSocket notification
        if (success) {
            sendAuthUpdate(
                accountId = accountId,
                type = AuthUpdateType.AUTHENTICATED,
                message = "Аутентификация успешно завершена!"
            )
        } else {
            sendAuthUpdate(
                accountId = accountId,
                type = AuthUpdateType.ERROR,
                message = errorMessage ?: "Ошибка аутентификации"
            )
        }
    }

    /**
     * Send authentication update via WebSocket
     */
    private fun sendAuthUpdate(
        accountId: Long,
        type: AuthUpdateType,
        message: String,
        context: Map<String, String>? = null
    ) {
        try {
            val update = AuthUpdate(
                accountId = accountId,
                type = type,
                message = message,
                context = context
            )

            // Send to specific account topic
            messagingTemplate.convertAndSend("/topic/telegram-auth/$accountId", update)

            logger.debug("WebSocket update sent for account $accountId: $type")

        } catch (e: Exception) {
            logger.error("Failed to send WebSocket update for account $accountId", e)
        }
    }

    /**
     * Cancel authentication for an account (cleanup)
     */
    fun cancelAuthentication(accountId: Long) {
        logger.info("Canceling authentication for account $accountId")

        pendingCodeRequests.remove(accountId)?.completeExceptionally(
            TimeoutException("Authentication canceled")
        )
        pendingPasswordRequests.remove(accountId)?.completeExceptionally(
            TimeoutException("Authentication canceled")
        )
    }

    /**
     * Get current authentication state for an account
     */
    fun getAuthenticationState(accountId: Long): Map<String, Any> {
        val hasPendingCode = pendingCodeRequests.containsKey(accountId)
        val hasPendingPassword = pendingPasswordRequests.containsKey(accountId)

        return mapOf(
            "accountId" to accountId,
            "waitingForCode" to hasPendingCode,
            "waitingForPassword" to hasPendingPassword,
            "isPending" to (hasPendingCode || hasPendingPassword)
        )
    }
}
