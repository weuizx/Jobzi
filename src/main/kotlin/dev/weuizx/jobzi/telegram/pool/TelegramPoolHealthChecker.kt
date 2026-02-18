package dev.weuizx.jobzi.telegram.pool

import dev.weuizx.jobzi.domain.TelegramAccountStatus
import dev.weuizx.jobzi.service.db.TelegramAccountPoolDbService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Scheduled health checks and cleanup for Telegram client pool.
 *
 * Features:
 * - Health check every 5 minutes (verify clients are responsive)
 * - Session cleanup at 2 AM daily (remove old session files)
 */
@Component
class TelegramPoolHealthChecker(
    private val poolManager: TelegramClientPoolManager,
    private val dbService: TelegramAccountPoolDbService,
    private val clientFactory: TelegramClientFactory
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Health check for all authenticated accounts (every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    fun checkAccountHealth() {
        try {
            logger.info("Running health check for Telegram pool...")

            val authenticatedAccounts = dbService.findActiveAccountsByStatus(TelegramAccountStatus.AUTHENTICATED)
            var healthyCount = 0
            var failedCount = 0

            authenticatedAccounts.forEach { account ->
                val client = poolManager.getClient(account.sessionName)

                if (client == null) {
                    logger.warn("Client not found in pool: ${account.sessionName}")
                    return@forEach
                }

                if (!client.isAuthenticated) {
                    logger.warn("Client not authenticated: ${account.sessionName}")
                    return@forEach
                }

                try {
                    // Try to get current user info (simple health check)
                    val me = client.getMe()

                    healthyCount++
                    logger.debug("✓ Account ${account.sessionName} is healthy (user ID: ${me.id})")

                } catch (e: Exception) {
                    failedCount++
                    logger.error("Health check failed for account: ${account.sessionName}", e)
                    handleUnhealthyAccount(account.id!!, account.sessionName, e.message ?: "Unknown error")
                }
            }

            if (authenticatedAccounts.isNotEmpty()) {
                logger.info("Health check completed: $healthyCount healthy, $failedCount failed out of ${authenticatedAccounts.size}")
            }

        } catch (e: Exception) {
            logger.error("Error during health check", e)
        }
    }

    /**
     * Handle unhealthy account - mark as error and remove from pool
     */
    private fun handleUnhealthyAccount(accountId: Long, sessionName: String, errorMessage: String) {
        try {
            logger.warn("Marking account as ERROR: $sessionName (reason: $errorMessage)")

            // Update status in database
            dbService.updateAccountStatus(
                accountId = accountId,
                status = TelegramAccountStatus.ERROR,
                errorMessage = "Health check failed: $errorMessage"
            )

            // Remove from pool
            poolManager.removeClient(sessionName)

        } catch (e: Exception) {
            logger.error("Failed to handle unhealthy account: $sessionName", e)
        }
    }

    /**
     * Cleanup old session directories (runs at 2 AM daily)
     */
    @Scheduled(cron = "0 0 2 * * *")
    fun cleanupOldSessions() {
        try {
            logger.info("Running session directory cleanup...")

            val inactiveAccounts = dbService.findAccountsByStatus(TelegramAccountStatus.INACTIVE)
            var deletedCount = 0

            inactiveAccounts.forEach { account ->
                // Check if client is actually not in pool
                val client = poolManager.getClient(account.sessionName)
                if (client != null) {
                    logger.debug("Skipping cleanup for ${account.sessionName} - client still in pool")
                    return@forEach
                }

                try {
                    // Delete session directory
                    clientFactory.deleteSessionDirectory(account.sessionName)
                    deletedCount++
                    logger.info("✓ Cleaned up session directory: ${account.sessionName}")

                } catch (e: Exception) {
                    logger.error("Failed to cleanup session: ${account.sessionName}", e)
                }
            }

            if (deletedCount > 0) {
                logger.info("Session cleanup completed: $deletedCount directories deleted")
            } else {
                logger.info("Session cleanup completed: no directories to delete")
            }

        } catch (e: Exception) {
            logger.error("Error during session cleanup", e)
        }
    }
}
