package dev.weuizx.jobzi.telegram.pool

import it.tdlight.client.AuthenticationSupplier
import org.slf4j.LoggerFactory

/**
 * Simple authentication supplier that uses console input for now.
 *
 * TODO: Replace with database/WebSocket based authentication in future version
 */
class DatabaseAuthenticationSupplier(
    private val accountId: Long,
    private val phoneNumber: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun createSupplier(): AuthenticationSupplier<*> {
        logger.info("Creating authentication supplier for account $accountId with phone $phoneNumber")

        // For now, use standard console-based authentication
        // TODO: Implement custom supplier with CompletableFuture for bot-based input
        return AuthenticationSupplier.user(phoneNumber)
    }
}
