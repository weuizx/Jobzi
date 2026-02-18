package dev.weuizx.jobzi.service.db

import dev.weuizx.jobzi.domain.*
import dev.weuizx.jobzi.repository.TelegramAccountRepository
import dev.weuizx.jobzi.repository.TelegramAuthSessionRepository
import dev.weuizx.jobzi.service.EncryptionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
@Transactional
class TelegramAccountPoolDbService(
    private val accountRepository: TelegramAccountRepository,
    private val authSessionRepository: TelegramAuthSessionRepository,
    private val encryptionService: EncryptionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ==================== Account Management ====================

    fun findAccountById(id: Long): TelegramAccount? =
        accountRepository.findById(id).orElse(null)

    fun findAccountBySessionName(sessionName: String): TelegramAccount? =
        accountRepository.findBySessionName(sessionName)

    fun findAccountsByStatus(status: TelegramAccountStatus): List<TelegramAccount> =
        accountRepository.findByStatus(status)

    fun findActiveAccountsByStatus(status: TelegramAccountStatus): List<TelegramAccount> =
        accountRepository.findByStatusAndIsActiveTrue(status)

    fun findAllAccounts(): List<TelegramAccount> =
        accountRepository.findAll()

    fun createAccount(
        phoneNumber: String,
        apiId: Int,
        apiHash: String,
        sessionName: String,
        createdByUser: User?
    ): TelegramAccount {
        // Check for duplicates
        val encryptedPhoneNumber = encryptionService.encrypt(phoneNumber)
        if (accountRepository.existsByPhoneNumberEncrypted(encryptedPhoneNumber)) {
            throw IllegalArgumentException("Account with this phone number already exists")
        }
        if (accountRepository.existsBySessionName(sessionName)) {
            throw IllegalArgumentException("Account with this session name already exists")
        }

        val account = TelegramAccount(
            phoneNumberEncrypted = encryptedPhoneNumber,
            apiId = apiId,
            apiHashEncrypted = encryptionService.encrypt(apiHash),
            sessionName = sessionName,
            status = TelegramAccountStatus.INACTIVE,
            createdByUser = createdByUser
        )

        return accountRepository.save(account)
    }

    fun updateAccountStatus(
        accountId: Long,
        status: TelegramAccountStatus,
        authState: TelegramAuthState? = null,
        errorMessage: String? = null
    ): TelegramAccount {
        val account = findAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        account.status = status
        account.authState = authState
        account.errorMessage = errorMessage
        account.updatedAt = OffsetDateTime.now()

        return accountRepository.save(account)
    }

    fun updateAccountLastUsedAt(accountId: Long): TelegramAccount {
        val account = findAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        account.lastUsedAt = OffsetDateTime.now()
        account.updatedAt = OffsetDateTime.now()

        return accountRepository.save(account)
    }

    fun deactivateAccount(accountId: Long): TelegramAccount {
        val account = findAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        account.isActive = false
        account.status = TelegramAccountStatus.INACTIVE
        account.updatedAt = OffsetDateTime.now()

        return accountRepository.save(account)
    }

    fun deleteAccount(accountId: Long) {
        accountRepository.deleteById(accountId)
    }

    fun decryptPhoneNumber(account: TelegramAccount): String =
        encryptionService.decrypt(account.phoneNumberEncrypted)

    fun decryptApiHash(account: TelegramAccount): String =
        encryptionService.decrypt(account.apiHashEncrypted)

    // ==================== Auth Session Management ====================

    fun createAuthSession(
        accountId: Long,
        authState: TelegramAuthSessionState,
        expirationMinutes: Long = 10
    ): TelegramAuthSession {
        val account = findAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")

        val session = TelegramAuthSession(
            account = account,
            authState = authState,
            expiresAt = OffsetDateTime.now().plusMinutes(expirationMinutes)
        )

        return authSessionRepository.save(session)
    }

    fun findAuthSession(accountId: Long, authState: TelegramAuthSessionState): TelegramAuthSession? =
        authSessionRepository.findByAccountIdAndAuthState(accountId, authState)

    fun findLatestAuthSession(accountId: Long): TelegramAuthSession? =
        authSessionRepository.findTopByAccountIdOrderByCreatedAtDesc(accountId)

    fun updateAuthSessionState(
        sessionId: Long,
        authState: TelegramAuthSessionState,
        codeHash: String? = null
    ): TelegramAuthSession {
        val session = authSessionRepository.findById(sessionId).orElse(null)
            ?: throw IllegalArgumentException("Auth session not found: $sessionId")

        session.authState = authState
        if (codeHash != null) {
            session.codeHash = codeHash
        }

        return authSessionRepository.save(session)
    }

    fun deleteAuthSession(sessionId: Long) {
        authSessionRepository.deleteById(sessionId)
    }

    // ==================== Scheduled Cleanup ====================

    /**
     * Cleanup expired auth sessions every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    fun cleanupExpiredAuthSessions() {
        try {
            val deletedCount = authSessionRepository.deleteByExpiresAtBefore(OffsetDateTime.now())
            if (deletedCount > 0) {
                logger.info("Cleaned up {} expired auth sessions", deletedCount)
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup expired auth sessions", e)
        }
    }
}
