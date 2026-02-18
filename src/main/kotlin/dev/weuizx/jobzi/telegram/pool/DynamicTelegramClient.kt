package dev.weuizx.jobzi.telegram.pool

import com.google.common.util.concurrent.RateLimiter
import dev.weuizx.jobzi.domain.TelegramAccount
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Wrapper around SimpleTelegramClient with additional features:
 * - Rate limiting to prevent hitting Telegram limits
 * - Authentication state tracking
 * - Last used timestamp tracking
 * - Lifecycle management
 */
class DynamicTelegramClient(
    private val client: SimpleTelegramClient,
    val account: TelegramAccount,
    private val onLastUsedUpdate: (Long, OffsetDateTime) -> Unit
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Rate limiter: 20 messages per minute (conservative limit)
    private val rateLimiter = RateLimiter.create(20.0 / 60.0)

    @Volatile
    var isAuthenticated: Boolean = false
        private set

    @Volatile
    var lastUsedAt: OffsetDateTime = OffsetDateTime.now()
        private set

    /**
     * Send a message to a chat with rate limiting
     */
    fun sendMessage(chatId: Long, messageText: String): Long {
        if (!isAuthenticated) {
            throw IllegalStateException("Client not authenticated for account: ${account.sessionName}")
        }

        // Wait for rate limiter permit
        rateLimiter.acquire()

        val inputMessageContent = TdApi.InputMessageText(
            TdApi.FormattedText(messageText, emptyArray()),
            null,
            true
        )

        val sendMessageRequest = TdApi.SendMessage(
            chatId,
            0,
            null,
            null,
            null,
            inputMessageContent
        )

        val result = client.send(sendMessageRequest).get(10, TimeUnit.SECONDS)

        if (result is TdApi.Message) {
            updateLastUsedAt()
            return result.id
        } else {
            throw RuntimeException("Unexpected response type: ${result?.javaClass?.simpleName}")
        }
    }

    /**
     * Get chat information
     */
    fun getChat(chatId: Long): TdApi.Chat {
        if (!isAuthenticated) {
            throw IllegalStateException("Client not authenticated for account: ${account.sessionName}")
        }

        val result = client.send(TdApi.GetChat(chatId)).get(10, TimeUnit.SECONDS)

        if (result is TdApi.Chat) {
            updateLastUsedAt()
            return result
        } else {
            throw RuntimeException("Unexpected response type: ${result?.javaClass?.simpleName}")
        }
    }

    /**
     * Get current user information (for health checks)
     */
    fun getMe(): TdApi.User {
        val user = client.meAsync.get(10, TimeUnit.SECONDS)
        updateLastUsedAt()
        return user
    }

    /**
     * Join a chat by invite link
     */
    fun joinChatByInviteLink(inviteLink: String): TdApi.Chat {
        if (!isAuthenticated) {
            throw IllegalStateException("Client not authenticated for account: ${account.sessionName}")
        }

        rateLimiter.acquire()

        val result = client.send(TdApi.JoinChatByInviteLink(inviteLink)).get(10, TimeUnit.SECONDS)

        if (result is TdApi.Chat) {
            updateLastUsedAt()
            return result
        } else {
            throw RuntimeException("Unexpected response type: ${result?.javaClass?.simpleName}")
        }
    }

    /**
     * Get list of chats
     */
    fun getChats(limit: Int = 20): List<TdApi.Chat> {
        if (!isAuthenticated) {
            throw IllegalStateException("Client not authenticated for account: ${account.sessionName}")
        }

        val chatsResult = client.send(TdApi.GetChats(null, limit)).get(10, TimeUnit.SECONDS)

        if (chatsResult is TdApi.Chats) {
            val chats = chatsResult.chatIds.toList().mapNotNull { chatId ->
                try {
                    val chat = client.send(TdApi.GetChat(chatId)).get(5, TimeUnit.SECONDS)
                    if (chat is TdApi.Chat) chat else null
                } catch (e: Exception) {
                    logger.warn("Failed to get chat $chatId", e)
                    null
                }
            }
            updateLastUsedAt()
            return chats
        } else {
            throw RuntimeException("Unexpected response type: ${chatsResult?.javaClass?.simpleName}")
        }
    }

    /**
     * Mark client as authenticated
     */
    fun markAsAuthenticated() {
        isAuthenticated = true
        logger.info("Client authenticated for account: ${account.sessionName}")
    }

    /**
     * Update last used timestamp
     */
    private fun updateLastUsedAt() {
        lastUsedAt = OffsetDateTime.now()
        account.id?.let { accountId ->
            try {
                onLastUsedUpdate(accountId, lastUsedAt)
            } catch (e: Exception) {
                logger.warn("Failed to update lastUsedAt for account ${account.sessionName}", e)
            }
        }
    }

    /**
     * Close the client gracefully
     */
    override fun close() {
        try {
            logger.info("Closing client for account: ${account.sessionName}")
            isAuthenticated = false
            client.close()
            logger.info("Client closed successfully for account: ${account.sessionName}")
        } catch (e: Exception) {
            logger.error("Error closing client for account: ${account.sessionName}", e)
        }
    }
}
