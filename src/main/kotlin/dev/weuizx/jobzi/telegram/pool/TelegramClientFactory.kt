package dev.weuizx.jobzi.telegram.pool

import dev.weuizx.jobzi.domain.TelegramAccount
import dev.weuizx.jobzi.service.EncryptionService
import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client.APIToken
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.client.TDLibSettings
import it.tdlight.jni.TdApi
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime

/**
 * Factory for creating TDLight clients with proper configuration.
 *
 * IMPORTANT: Uses a single global SimpleTelegramClientFactory instance
 * as required by TDLight library design.
 */
@Component
class TelegramClientFactory(
    @Value("\${telegram.account-pool.session-base-path}")
    private val sessionBasePath: String,
    private val encryptionService: EncryptionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Single global factory instance (required by TDLight)
    private lateinit var clientFactory: SimpleTelegramClientFactory

    @Volatile
    private var isInitialized = false

    @PostConstruct
    fun init() {
        try {
            logger.info("Initializing TelegramClientFactory...")

            // Initialize TDLight native libraries (only once globally)
            Init.init()
            logger.info("✓ TDLight native libraries initialized")

            // Setup logging
            Log.setLogMessageHandler(1, Slf4JLogMessageHandler())
            logger.info("✓ TDLight logging configured")

            // Create global factory
            clientFactory = SimpleTelegramClientFactory()
            logger.info("✓ SimpleTelegramClientFactory created")

            // Ensure base session directory exists
            val basePath = Paths.get(sessionBasePath)
            Files.createDirectories(basePath)
            logger.info("✓ Session base path: ${basePath.toAbsolutePath()}")

            isInitialized = true
            logger.info("TelegramClientFactory initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize TelegramClientFactory", e)
            throw RuntimeException("Failed to initialize TelegramClientFactory", e)
        }
    }

    /**
     * Create a new TDLight client for the given account
     *
     * @param account The Telegram account to create client for
     * @param onLastUsedUpdate Callback to update lastUsedAt timestamp
     * @param onAuthStateChanged Callback when auth state changes
     * @return Wrapped client instance
     */
    fun createClient(
        account: TelegramAccount,
        onLastUsedUpdate: (Long, OffsetDateTime) -> Unit,
        onAuthStateChanged: (Long, TdApi.UpdateAuthorizationState) -> Unit
    ): DynamicTelegramClient {
        if (!isInitialized) {
            throw IllegalStateException("TelegramClientFactory not initialized")
        }

        account.id ?: throw IllegalArgumentException("Account ID must not be null")

        try {
            logger.info("Creating client for account: ${account.sessionName}")

            // Decrypt credentials
            val apiHash = encryptionService.decrypt(account.apiHashEncrypted)
            val phoneNumber = encryptionService.decrypt(account.phoneNumberEncrypted)

            // Create API token
            val apiToken = APIToken(account.apiId, apiHash)

            // Create TDLib settings
            val settings = TDLibSettings.create(apiToken)

            // Configure session paths (unique per account)
            val sessionPath = getSessionPath(account.sessionName)
            Files.createDirectories(sessionPath)
            settings.databaseDirectoryPath = sessionPath.resolve("data")
            settings.downloadedFilesDirectoryPath = sessionPath.resolve("downloads")

            logger.info("✓ Session path configured: ${sessionPath.toAbsolutePath()}")

            // Create authentication supplier
            val authSupplier = DatabaseAuthenticationSupplier(
                accountId = account.id!!,
                phoneNumber = phoneNumber
            ).createSupplier()

            // Build client with auth state handler
            val clientBuilder = clientFactory.builder(settings)

            // Add authorization state update handler
            clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
                handleAuthStateUpdate(account.id!!, update, onAuthStateChanged)
            }

            // Build the client
            val simpleTelegramClient = clientBuilder.build(authSupplier)

            logger.info("✓ Client created for account: ${account.sessionName}")

            // Wrap in DynamicTelegramClient
            return DynamicTelegramClient(
                client = simpleTelegramClient,
                account = account,
                onLastUsedUpdate = onLastUsedUpdate
            )

        } catch (e: Exception) {
            logger.error("Failed to create client for account: ${account.sessionName}", e)
            throw RuntimeException("Failed to create client for account ${account.sessionName}", e)
        }
    }

    /**
     * Handle authorization state updates
     */
    private fun handleAuthStateUpdate(
        accountId: Long,
        update: TdApi.UpdateAuthorizationState,
        onAuthStateChanged: (Long, TdApi.UpdateAuthorizationState) -> Unit
    ) {
        when (val state = update.authorizationState) {
            is TdApi.AuthorizationStateReady -> {
                logger.info("✅ Account $accountId authenticated successfully")
            }
            is TdApi.AuthorizationStateWaitCode -> {
                logger.info("⏳ Account $accountId waiting for authentication code")
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                logger.info("⏳ Account $accountId waiting for 2FA password")
            }
            is TdApi.AuthorizationStateClosed -> {
                logger.info("❌ Account $accountId connection closed")
            }
            else -> {
                logger.debug("Account $accountId auth state: ${state.javaClass.simpleName}")
            }
        }

        // Notify listener
        try {
            onAuthStateChanged(accountId, update)
        } catch (e: Exception) {
            logger.error("Error in auth state change handler for account $accountId", e)
        }
    }

    /**
     * Get session directory path for an account
     */
    fun getSessionPath(sessionName: String): Path {
        return Paths.get(sessionBasePath).resolve(sessionName)
    }

    /**
     * Delete session directory for an account
     */
    fun deleteSessionDirectory(sessionName: String) {
        try {
            val sessionPath = getSessionPath(sessionName)
            if (Files.exists(sessionPath)) {
                Files.walk(sessionPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
                logger.info("✓ Deleted session directory: $sessionName")
            }
        } catch (e: Exception) {
            logger.error("Failed to delete session directory: $sessionName", e)
        }
    }
}
