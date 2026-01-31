package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.User
import dev.weuizx.jobzi.domain.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByTelegramId(telegramId: Long): User?
    fun existsByTelegramId(telegramId: Long): Boolean
    fun findByRole(role: UserRole): List<User>
}