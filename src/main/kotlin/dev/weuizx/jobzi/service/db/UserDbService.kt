package dev.weuizx.jobzi.service.db

import dev.weuizx.jobzi.domain.User
import dev.weuizx.jobzi.domain.UserRole
import dev.weuizx.jobzi.repository.BusinessUserRepository
import dev.weuizx.jobzi.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserDbService(
    private val userRepository: UserRepository,
    private val businessUserRepository: BusinessUserRepository
) {
    fun findById(id: Long): User? = userRepository.findById(id).orElse(null)

    fun findByTelegramId(telegramId: Long): User? = userRepository.findByTelegramId(telegramId)

    fun existsByTelegramId(telegramId: Long): Boolean = userRepository.existsByTelegramId(telegramId)

    fun findSuperAdmins(): List<User> = userRepository.findByRole(UserRole.SUPERADMIN)

    fun save(user: User): User = userRepository.save(user)

    fun createUser(
        telegramId: Long,
        firstName: String? = null,
        lastName: String? = null,
        username: String? = null,
        role: UserRole = UserRole.USER
    ): User {
        val user = User(
            telegramId = telegramId,
            firstName = firstName,
            lastName = lastName,
            username = username,
            role = role
        )
        return userRepository.save(user)
    }

    /**
     * Проверяет, является ли пользователь представителем какого-либо бизнеса
     */
    fun isBusinessRepresentative(telegramId: Long): Boolean {
        val user = findByTelegramId(telegramId) ?: return false
        return user.id?.let { businessUserRepository.existsByUserId(it) } ?: false
    }

    /**
     * Проверяет, является ли пользователь суперадмином
     */
    fun isSuperAdmin(telegramId: Long): Boolean {
        val user = findByTelegramId(telegramId) ?: return false
        return user.role == UserRole.SUPERADMIN
    }
}