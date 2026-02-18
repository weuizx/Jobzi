package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.TelegramAuthSession
import dev.weuizx.jobzi.domain.TelegramAuthSessionState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface TelegramAuthSessionRepository : JpaRepository<TelegramAuthSession, Long> {
    fun findByAccountIdAndAuthState(accountId: Long, authState: TelegramAuthSessionState): TelegramAuthSession?
    fun findTopByAccountIdOrderByCreatedAtDesc(accountId: Long): TelegramAuthSession?

    @Modifying
    @Query("DELETE FROM TelegramAuthSession t WHERE t.expiresAt < :now")
    fun deleteByExpiresAtBefore(now: OffsetDateTime): Int
}
