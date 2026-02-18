package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.TelegramAccount
import dev.weuizx.jobzi.domain.TelegramAccountStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TelegramAccountRepository : JpaRepository<TelegramAccount, Long> {
    fun findBySessionName(sessionName: String): TelegramAccount?
    fun findByStatus(status: TelegramAccountStatus): List<TelegramAccount>
    fun findByStatusAndIsActiveTrue(status: TelegramAccountStatus): List<TelegramAccount>
    fun existsBySessionName(sessionName: String): Boolean
    fun existsByPhoneNumberEncrypted(phoneNumberEncrypted: String): Boolean
}
