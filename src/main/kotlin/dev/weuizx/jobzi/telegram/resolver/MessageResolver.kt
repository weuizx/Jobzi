package dev.weuizx.jobzi.telegram.resolver

import dev.weuizx.jobzi.domain.UserRole
import dev.weuizx.jobzi.service.db.UserDbService
import dev.weuizx.jobzi.telegram.dto.IncomingMessage
import dev.weuizx.jobzi.telegram.handler.ApplicantHandler
import dev.weuizx.jobzi.telegram.handler.BusinessHandler
import dev.weuizx.jobzi.telegram.handler.SuperAdminHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Резолвер для определения типа пользователя и маршрутизации к соответствующему хендлеру
 */
@Component
class MessageResolver(
    private val userDbService: UserDbService,
    private val superAdminHandler: SuperAdminHandler,
    private val businessHandler: BusinessHandler,
    private val applicantHandler: ApplicantHandler
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Обрабатывает входящее сообщение, определяет тип пользователя и передает в нужный хендлер
     */
    fun resolve(message: IncomingMessage): String {
        log.debug("Resolving message from user ${message.userId}: ${message.text}")

        // Определяем тип пользователя
        val user = userDbService.findByTelegramId(message.userId)

        return when {
            user == null -> {
                // Новый пользователь - по умолчанию соискатель
                log.info("New user detected: ${message.userId}, treating as applicant")
                applicantHandler.handle(message)
            }

            user.role == UserRole.SUPERADMIN -> {
                log.debug("Routing to SuperAdminHandler for user ${message.userId}")
                superAdminHandler.handle(message)
            }

            // Проверяем, является ли пользователь представителем бизнеса
            userDbService.isBusinessRepresentative(message.userId) -> {
                log.debug("Routing to BusinessHandler for user ${message.userId}")
                businessHandler.handle(message)
            }

            else -> {
                // Обычный пользователь - соискатель
                log.debug("Routing to ApplicantHandler for user ${message.userId}")
                applicantHandler.handle(message)
            }
        }
    }
}