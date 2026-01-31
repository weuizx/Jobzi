package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.Business
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessRepository : JpaRepository<Business, Long> {
    fun findByTelegramChatId(telegramChatId: Long): Business?
    fun existsByTelegramChatId(telegramChatId: Long): Boolean
    fun findByIsActive(isActive: Boolean): List<Business>
}