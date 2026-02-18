package dev.weuizx.jobzi.telegram.pool

import dev.weuizx.jobzi.domain.TelegramAccount
import dev.weuizx.jobzi.domain.TelegramAccountStatus
import dev.weuizx.jobzi.service.db.TelegramAccountPoolDbService
import it.tdlight.jni.TdApi
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages a pool of Telegram clients with round-robin load balancing.
 *
 * Features:
 * - Loads authenticated accounts on startup
 * - Round-robin client selection
 * - Graceful shutdown
 * - Thread-safe operations
 */
@Service
class TelegramClientPoolManager(
    private val clientFactory: TelegramClientFactory,
    private val dbService: TelegramAccountPoolDbService,
    private val authenticationService: TelegramAuthenticationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Pool of active clients: sessionName -> DynamicTelegramClient
    private val pool = ConcurrentHashMap<String, DynamicTelegramClient>()

    // Round-robin index for load balancing
    private val roundRobinIndex = AtomicInteger(0)

    @PostConstruct
    fun initialize() {
        try {
            logger.info("╔═══════════════════════════════════════════════════════════════╗")
            logger.info("║  Initializing Telegram Client Pool Manager                   ║")
            logger.info("╚═══════════════════════════════════════════════════════════════╝")

            // Load authenticated accounts from database
            val authenticatedAccounts = dbService.findActiveAccountsByStatus(TelegramAccountStatus.AUTHENTICATED)
            logger.info("Found ${authenticatedAccounts.size} authenticated accounts")

            // Start each authenticated account
            authenticatedAccounts.forEach { account ->
                try {
                    logger.info("Loading account: ${account.sessionName}")
                    loadAuthenticatedAccount(account)
                } catch (e: Exception) {
                    logger.error("Failed to load account: ${account.sessionName}", e)
                    // Mark account as error
                    dbService.updateAccountStatus(
                        accountId = account.id!!,
                        status = TelegramAccountStatus.ERROR,
                        errorMessage = "Failed to load on startup: ${e.message}"
                    )
                }
            }

            logger.info("╔═══════════════════════════════════════════════════════════════╗")
            logger.info("║  Pool initialized with ${pool.size} active clients                    ║")
            logger.info("╚═══════════════════════════════════════════════════════════════╝")

        } catch (e: Exception) {
            logger.error("Failed to initialize Telegram Client Pool Manager", e)
        }
    }

    /**
     * Start authentication for a new account
     *
     * @param accountId ID of the account to authenticate
     * @return The client being authenticated
     */
    fun startAuthentication(accountId: Long): DynamicTelegramClient {
        val account = dbService.findAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        // Check if already in pool
        if (pool.containsKey(account.sessionName)) {
            throw IllegalStateException("Account already active: ${account.sessionName}")
        }

        logger.info("Starting authentication for account: ${account.sessionName}")

        // Update status to AUTHENTICATING
        dbService.updateAccountStatus(
            accountId = accountId,
            status = TelegramAccountStatus.AUTHENTICATING
        )

        // Create client with authentication callbacks
        val client = clientFactory.createClient(
            account = account,
            onLastUsedUpdate = { accId, timestamp -> handleLastUsedUpdate(accId, timestamp) },
            onAuthStateChanged = { accId, state -> handleAuthStateChange(accId, state) }
        )

        // Add to pool (not yet authenticated)
        pool[account.sessionName] = client

        logger.info("Client created and added to pool: ${account.sessionName}")
        return client
    }

    /**
     * Load an already authenticated account into the pool
     */
    private fun loadAuthenticatedAccount(account: TelegramAccount) {
        if (pool.containsKey(account.sessionName)) {
            logger.warn("Account already in pool: ${account.sessionName}")
            return
        }

        // Create client (it should auto-authenticate from saved session)
        val client = clientFactory.createClient(
            account = account,
            onLastUsedUpdate = { accId, timestamp -> handleLastUsedUpdate(accId, timestamp) },
            onAuthStateChanged = { accId, state -> handleAuthStateChange(accId, state) }
        )

        // Mark as authenticated (assuming saved session is valid)
        client.markAsAuthenticated()

        // Add to pool
        pool[account.sessionName] = client

        logger.info("✓ Account loaded into pool: ${account.sessionName}")
    }

    /**
     * Get next available client using round-robin
     *
     * @return Next available client or null if pool is empty
     */
    fun getNextAvailableClient(): DynamicTelegramClient? {
        val clients = pool.values.filter { it.isAuthenticated }

        if (clients.isEmpty()) {
            logger.warn("No authenticated clients available in pool")
            return null
        }

        // Round-robin selection
        val index = roundRobinIndex.getAndIncrement() % clients.size
        val client = clients.elementAt(index)

        logger.debug("Selected client: ${client.account.sessionName} (index: $index)")
        return client
    }

    /**
     * Get client by session name
     */
    fun getClient(sessionName: String): DynamicTelegramClient? {
        return pool[sessionName]
    }

    /**
     * Remove client from pool and close it
     */
    fun removeClient(sessionName: String) {
        val client = pool.remove(sessionName)
        if (client != null) {
            logger.info("Removing client from pool: $sessionName")
            client.close()

            // Update database status
            val account = dbService.findAccountBySessionName(sessionName)
            if (account != null) {
                dbService.updateAccountStatus(
                    accountId = account.id!!,
                    status = TelegramAccountStatus.INACTIVE
                )
            }

            logger.info("✓ Client removed: $sessionName")
        }
    }

    /**
     * Get pool statistics
     */
    fun getPoolStatus(): Map<String, Any> {
        val totalClients = pool.size
        val authenticatedClients = pool.values.count { it.isAuthenticated }

        return mapOf(
            "totalClients" to totalClients,
            "authenticatedClients" to authenticatedClients,
            "authenticatingClients" to (totalClients - authenticatedClients),
            "clients" to pool.values.map { client ->
                mapOf(
                    "sessionName" to client.account.sessionName,
                    "isAuthenticated" to client.isAuthenticated,
                    "lastUsedAt" to client.lastUsedAt.toString()
                )
            }
        )
    }

    /**
     * Handle last used timestamp update
     */
    private fun handleLastUsedUpdate(accountId: Long, timestamp: OffsetDateTime) {
        try {
            dbService.updateAccountLastUsedAt(accountId)
        } catch (e: Exception) {
            logger.warn("Failed to update lastUsedAt for account $accountId", e)
        }
    }

    /**
     * Handle authentication state changes
     */
    private fun handleAuthStateChange(accountId: Long, update: TdApi.UpdateAuthorizationState) {
        try {
            val account = dbService.findAccountById(accountId) ?: return

            when (val state = update.authorizationState) {
                is TdApi.AuthorizationStateReady -> {
                    // Mark client as authenticated
                    pool[account.sessionName]?.markAsAuthenticated()

                    // Update database
                    dbService.updateAccountStatus(
                        accountId = accountId,
                        status = TelegramAccountStatus.AUTHENTICATED,
                        authState = null,
                        errorMessage = null
                    )

                    // Notify authentication service
                    authenticationService.notifyAuthenticationComplete(accountId, success = true)

                    logger.info("✅ Account authenticated: ${account.sessionName}")
                }

                is TdApi.AuthorizationStateClosed -> {
                    // Remove from pool
                    pool.remove(account.sessionName)

                    // Update database
                    dbService.updateAccountStatus(
                        accountId = accountId,
                        status = TelegramAccountStatus.ERROR,
                        errorMessage = "Connection closed"
                    )

                    logger.warn("❌ Account connection closed: ${account.sessionName}")
                }

                is TdApi.AuthorizationStateWaitCode -> {
                    logger.info("⏳ Account waiting for code: ${account.sessionName}")
                }

                is TdApi.AuthorizationStateWaitPassword -> {
                    logger.info("⏳ Account waiting for password: ${account.sessionName}")
                }

                else -> {
                    logger.debug("Auth state for ${account.sessionName}: ${state.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling auth state change for account $accountId", e)
        }
    }

    /**
     * Graceful shutdown of all clients
     */
    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down Telegram Client Pool Manager...")

        pool.forEach { (sessionName, client) ->
            try {
                logger.info("Closing client: $sessionName")
                client.close()
            } catch (e: Exception) {
                logger.error("Error closing client: $sessionName", e)
            }
        }

        pool.clear()
        logger.info("✓ Telegram Client Pool Manager shut down successfully")
    }
}
