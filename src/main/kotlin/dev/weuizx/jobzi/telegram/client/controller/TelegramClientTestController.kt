package dev.weuizx.jobzi.telegram.client.controller

import dev.weuizx.jobzi.telegram.client.dto.SendMessageByUsernameRequest
import dev.weuizx.jobzi.telegram.client.dto.SendMessageRequest
import dev.weuizx.jobzi.telegram.client.dto.SendMessageResponse
import dev.weuizx.jobzi.telegram.client.service.TelegramUserClientService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/telegram-client/test")
@ConditionalOnProperty(prefix = "telegram.client", name = ["enabled"], havingValue = "true")
class TelegramClientTestController(
    private val telegramUserClientService: TelegramUserClientService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Проверка статуса клиента
     * GET /api/telegram-client/test/status
     */
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "authenticated" to telegramUserClientService.isReady(),
                "message" to if (telegramUserClientService.isReady()) {
                    "Client is ready"
                } else {
                    "Client is not authenticated"
                }
            )
        )
    }

    /**
     * Отправить тестовое сообщение
     * POST /api/telegram-client/test/send
     * Body: {"chatId": -1001234567890, "message": "Hello!"}
     */
    @PostMapping("/send")
    fun sendMessage(@RequestBody request: SendMessageRequest): ResponseEntity<SendMessageResponse> {
        return try {
            logger.info("Sending message to chat ${request.chatId}: ${request.message}")

            val messageId = telegramUserClientService.sendMessage(request.chatId, request.message)

            ResponseEntity.ok(
                SendMessageResponse(
                    success = true,
                    message = "Message sent successfully",
                    messageId = messageId
                )
            )
        } catch (e: IllegalStateException) {
            logger.error("Client not authenticated", e)
            ResponseEntity.badRequest().body(
                SendMessageResponse(
                    success = false,
                    message = "Client is not authenticated: ${e.message}"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to send message", e)
            ResponseEntity.internalServerError().body(
                SendMessageResponse(
                    success = false,
                    message = "Failed to send message: ${e.message}"
                )
            )
        }
    }

    /**
     * Получить список чатов (для отладки)
     * GET /api/telegram-client/test/chats
     */
    @GetMapping("/chats")
    fun getChats(): ResponseEntity<Map<String, Any>> {
        return try {
            val chats = telegramUserClientService.getChats(20)

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "chats" to chats
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to get chats", e)
            ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to get chats: ${e.message}"
                )
            )
        }
    }

    /**
     * Отправить сообщение в публичный чат по username
     * POST /api/telegram-client/test/send-by-username
     * Body: {"username": "chatname", "message": "Hello!"}
     * или: {"username": "@chatname", "message": "Hello!"}
     */
    @PostMapping("/send-by-username")
    fun sendMessageByUsername(@RequestBody request: SendMessageByUsernameRequest): ResponseEntity<SendMessageResponse> {
        return try {
            logger.info("Sending message to @${request.username}: ${request.message}")

            val (messageId, chatId) = telegramUserClientService.sendMessageByUsername(
                request.username,
                request.message
            )

            ResponseEntity.ok(
                SendMessageResponse(
                    success = true,
                    message = "Message sent successfully to @${request.username}",
                    messageId = messageId,
                    chatId = chatId
                )
            )
        } catch (e: IllegalStateException) {
            logger.error("Client not authenticated", e)
            ResponseEntity.badRequest().body(
                SendMessageResponse(
                    success = false,
                    message = "Client is not authenticated: ${e.message}"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to send message by username", e)
            ResponseEntity.internalServerError().body(
                SendMessageResponse(
                    success = false,
                    message = "Failed to send message: ${e.message}"
                )
            )
        }
    }
}
