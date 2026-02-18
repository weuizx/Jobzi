package dev.weuizx.jobzi.telegram.client.service

import dev.weuizx.jobzi.telegram.client.config.TelegramClientProperties
import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client.APIToken
import it.tdlight.client.AuthenticationSupplier
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.client.TDLibSettings
import it.tdlight.jni.TdApi
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Сервис для работы с Telegram User Client API через TDLight
 *
 * Основано на официальном примере:
 * https://github.com/tdlight-team/tdlight-java/blob/master/example/src/main/java/it/tdlight/example/Example.java
 */
@Service
@ConditionalOnProperty(prefix = "telegram.client", name = ["enabled"], havingValue = "true")
class TelegramUserClientService(
    private val properties: TelegramClientProperties
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var clientFactory: SimpleTelegramClientFactory? = null
    private var client: SimpleTelegramClient? = null

    @Volatile
    private var isAuthenticated = false

    @PostConstruct
    fun init() {
        try {
            logger.info("╔═══════════════════════════════════════════════════════════════╗")
            logger.info("║  Initializing TDLight Telegram User Client                    ║")
            logger.info("╚═══════════════════════════════════════════════════════════════╝")

            // 1. Инициализация TDLight нативных библиотек
            Init.init()

            // 2. Настройка логирования
            Log.setLogMessageHandler(1, Slf4JLogMessageHandler())
            logger.info("✓ TDLight native libraries initialized")

            // 3. Создание API токена
            val apiToken = APIToken(properties.apiId, properties.apiHash)
            logger.info("✓ API Token created (API ID: ${properties.apiId})")

            // 4. Настройка TDLib
            val settings = TDLibSettings.create(apiToken)

            // 5. Настройка путей для сессии
            val sessionPath = Paths.get(properties.sessionPath)
            settings.databaseDirectoryPath = sessionPath.resolve("data")
            settings.downloadedFilesDirectoryPath = sessionPath.resolve("downloads")
            logger.info("✓ Session path: ${sessionPath.toAbsolutePath()}")

            // 6. Создание фабрики клиентов (ВАЖНО: только один экземпляр глобально!)
            clientFactory = SimpleTelegramClientFactory()
            logger.info("✓ Client factory created")

            // 7. Создание билдера клиента
            val clientBuilder = clientFactory!!.builder(settings)

            // 8. Добавление обработчика статуса авторизации
            clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
                onUpdateAuthorizationState(update)
            }

            // 9. Настройка аутентификации
            // Используем консольную аутентификацию для user аккаунта
            val authenticationData = AuthenticationSupplier.user(properties.phoneNumber)
            logger.info("✓ Authentication supplier created for: ${properties.phoneNumber}")

            // 10. Построение и запуск клиента
            client = clientBuilder.build(authenticationData)
            logger.info("✓ Client built and started")

            logger.info("╔═══════════════════════════════════════════════════════════════╗")
            logger.info("║  TDLight client is starting...                                ║")
            logger.info("║  Please check console for authentication prompts              ║")
            logger.info("║  (you may need to enter code from Telegram)                   ║")
            logger.info("╚═══════════════════════════════════════════════════════════════╝")

        } catch (e: Exception) {
            logger.error("❌ Failed to initialize TDLight client", e)
            logger.warn("Telegram User Client will not be available")
            logger.warn("Error details: ${e.message}")
        }
    }

    /**
     * Обработчик изменения статуса авторизации
     */
    private fun onUpdateAuthorizationState(update: TdApi.UpdateAuthorizationState) {
        when (val state = update.authorizationState) {
            is TdApi.AuthorizationStateReady -> {
                isAuthenticated = true
                logger.info("✅ LOGGED IN - Client is ready!")

                // Получаем информацию о текущем пользователе
                try {
                    val me = client?.meAsync?.get(30, TimeUnit.SECONDS)
                    if (me != null) {
                        logger.info("✅ Authenticated as: ${me.firstName} ${me.lastName} (ID: ${me.id})")
                        logger.info("   Username: @${me.usernames?.activeUsernames?.firstOrNull() ?: "none"}")
                        logger.info("   Phone: ${me.phoneNumber}")
                    }
                } catch (e: Exception) {
                    logger.warn("Could not fetch user info: ${e.message}")
                }
            }

            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                logger.info("⏳ Waiting for TDLib parameters...")
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                logger.info("⏳ Waiting for phone number...")
            }

            is TdApi.AuthorizationStateWaitCode -> {
                logger.info("⏳ Waiting for authentication code from Telegram...")
                logger.info("   Check your Telegram app for the code!")
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                logger.info("⏳ Waiting for 2FA password...")
            }

            is TdApi.AuthorizationStateClosing -> {
                logger.info("⏳ Closing...")
                isAuthenticated = false
            }

            is TdApi.AuthorizationStateClosed -> {
                logger.info("❌ Closed")
                isAuthenticated = false
            }

            is TdApi.AuthorizationStateLoggingOut -> {
                logger.info("⏳ Logging out...")
                isAuthenticated = false
            }

            else -> {
                logger.debug("Authorization state: ${state.javaClass.simpleName}")
            }
        }
    }

    /**
     * Отправить сообщение в чат
     *
     * @param chatId ID чата
     * @param messageText Текст сообщения
     * @return ID отправленного сообщения
     */
    fun sendMessage(chatId: Long, messageText: String): Long {
        if (!isAuthenticated || client == null) {
            throw IllegalStateException("Client is not authenticated. Please wait for authentication to complete.")
        }

        return try {
            logger.info("📤 Sending message to chat $chatId...")

            // Создаем текстовое сообщение
            val inputMessageContent = TdApi.InputMessageText(
                TdApi.FormattedText(messageText, emptyArray()),
                null,  // linkPreviewOptions
                true   // clearDraft
            )

            // Создаем запрос на отправку
            val sendMessageRequest = TdApi.SendMessage(
                chatId,
                0,     // messageThreadId
                null,  // replyTo
                null,  // options
                null,  // replyMarkup
                inputMessageContent
            )

            // Отправляем сообщение синхронно
            val result = client!!.send(sendMessageRequest).get(10, TimeUnit.SECONDS)

            if (result is TdApi.Message) {
                logger.info("✅ Message sent successfully!")
                logger.info("   Chat ID: $chatId")
                logger.info("   Message ID: ${result.id}")
                result.id
            } else {
                logger.error("❌ Unexpected response type: ${result?.javaClass?.simpleName}")
                throw RuntimeException("Failed to send message: unexpected response type")
            }

        } catch (e: Exception) {
            logger.error("❌ Failed to send message to chat $chatId", e)
            throw RuntimeException("Failed to send message: ${e.message}", e)
        }
    }

    /**
     * Получить список диалогов (чатов)
     *
     * @param limit Количество чатов для получения
     * @return Список чатов с ID, названием и типом
     */
    fun getChats(limit: Int = 20): List<Map<String, Any>> {
        if (!isAuthenticated || client == null) {
            throw IllegalStateException("Client is not authenticated. Please wait for authentication to complete.")
        }

        return try {
            logger.info("📋 Fetching chat list (limit: $limit)...")

            // Получаем список ID чатов
            val getChatsRequest = TdApi.GetChats(null, limit)
            val chatsResult = client!!.send(getChatsRequest).get(10, TimeUnit.SECONDS)

            if (chatsResult is TdApi.Chats) {
                val chatList = chatsResult.chatIds.toList().mapNotNull { chatId ->
                    try {
                        // Получаем информацию о каждом чате
                        val chat = client!!.send(TdApi.GetChat(chatId)).get(5, TimeUnit.SECONDS)

                        if (chat is TdApi.Chat) {
                            val chatType = when (chat.type) {
                                is TdApi.ChatTypePrivate -> "User"
                                is TdApi.ChatTypeBasicGroup -> "Group"
                                is TdApi.ChatTypeSupergroup -> {
                                    val supergroup = chat.type as TdApi.ChatTypeSupergroup
                                    if (supergroup.isChannel) "Channel" else "Supergroup"
                                }

                                is TdApi.ChatTypeSecret -> "Secret"
                                else -> "Unknown"
                            }

                            mapOf(
                                "id" to chat.id,
                                "title" to chat.title,
                                "type" to chatType
                            )
                        } else null
                    } catch (e: Exception) {
                        logger.warn("⚠ Failed to get info for chat $chatId: ${e.message}")
                        null
                    }
                }

                logger.info("✅ Fetched ${chatList.size} chats")
                chatList
            } else {
                logger.error("❌ Unexpected response type: ${chatsResult?.javaClass?.simpleName}")
                emptyList()
            }

        } catch (e: Exception) {
            logger.error("❌ Failed to get chats", e)
            throw RuntimeException("Failed to get chats: ${e.message}", e)
        }
    }

    /**
     * Отправить сообщение в публичный чат по username
     *
     * @param username Username чата (с @ или без, например: "chatname" или "@chatname")
     * @param messageText Текст сообщения
     * @return Пара: ID отправленного сообщения и ID чата
     */
    fun sendMessageByUsername(username: String, messageText: String): Pair<Long, Long> {
        if (!isAuthenticated || client == null) {
            throw IllegalStateException("Client is not authenticated. Please wait for authentication to complete.")
        }

        return try {
            // Убираем @ если есть
            val cleanUsername = username.removePrefix("@")
            logger.info("🔍 Searching for public chat: @$cleanUsername")

            // Ищем публичный чат по username
            val searchRequest = TdApi.SearchPublicChat(cleanUsername)
            val chatResult = client!!.send(searchRequest).get(10, TimeUnit.SECONDS)

            if (chatResult is TdApi.Chat) {
                val chatId = chatResult.id
                logger.info("✅ Found chat: ${chatResult.title} (ID: $chatId)")

                // Пытаемся отправить сообщение
                try {
                    val messageId = sendMessage(chatId, messageText)
                    logger.info("✅ Message sent to @$cleanUsername")
                    Pair(messageId, chatId)
                } catch (e: Exception) {
                    // Проверяем, не из-за отсутствия доступа ли ошибка
                    if (e.message?.contains("no write access", ignoreCase = true) == true) {
                        logger.warn("⚠️ No write access to chat. Attempting to join...")

                        // Вступаем в чат
                        joinChat(chatId, chatResult.title)

                        // Повторяем отправку сообщения
                        logger.info("🔄 Retrying message send after joining...")
                        val messageId = sendMessage(chatId, messageText)
                        logger.info("✅ Message sent to @$cleanUsername after joining")
                        Pair(messageId, chatId)
                    } else {
                        throw e
                    }
                }
            } else {
                logger.error("❌ Chat not found: @$cleanUsername")
                throw RuntimeException("Public chat not found: @$cleanUsername")
            }

        } catch (e: Exception) {
            logger.error("❌ Failed to send message to @$username", e)
            throw RuntimeException("Failed to send message to @$username: ${e.message}", e)
        }
    }

    /**
     * Вступить в чат/канал
     *
     * @param chatId ID чата
     * @param chatTitle Название чата (для логирования)
     */
    private fun joinChat(chatId: Long, chatTitle: String) {
        try {
            logger.info("📥 Joining chat: $chatTitle (ID: $chatId)")

            val joinRequest = TdApi.JoinChat(chatId)
            client!!.send(joinRequest).get(10, TimeUnit.SECONDS)

            logger.info("✅ Successfully joined chat: $chatTitle")

            // Даем немного времени на обновление прав доступа
            Thread.sleep(1000)

        } catch (e: Exception) {
            logger.error("❌ Failed to join chat: $chatTitle", e)
            throw RuntimeException("Failed to join chat: ${e.message}", e)
        }
    }

    /**
     * Проверить, готов ли клиент к работе
     */
    fun isReady(): Boolean = isAuthenticated && client != null

    @PreDestroy
    override fun close() {
        try {
            logger.info("Closing TDLight client...")
            client?.close()
            clientFactory?.close()
            logger.info("✓ TDLight client closed successfully")
        } catch (e: Exception) {
            logger.error("Error closing TDLight client", e)
        }
    }
}
