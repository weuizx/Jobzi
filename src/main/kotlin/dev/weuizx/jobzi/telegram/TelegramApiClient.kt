package dev.weuizx.jobzi.telegram

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Chat
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.ByteArrayInputStream

/**
 * Клиент для выполнения Telegram API запросов
 * Отделен от JobziBot для избежания циклических зависимостей
 */
@Component
class TelegramApiClient(
    private val botConfig: TelegramBotConfig
) : TelegramLongPollingBot() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBotToken(): String = botConfig.token

    override fun getBotUsername(): String = botConfig.username

    override fun onUpdateReceived(update: Update) {
        // Этот клиент используется только для API вызовов, не для получения обновлений
    }

    /**
     * Получает информацию о чате
     */
    @Throws(TelegramApiException::class)
    fun getChat(chatId: String): Chat {
        val getChat = GetChat()
        getChat.chatId = chatId
        return execute(getChat)
    }

    /**
     * Получает информацию о члене чата
     */
    @Throws(TelegramApiException::class)
    fun getChatMember(chatId: String, userId: Long): ChatMember {
        val getChatMember = GetChatMember()
        getChatMember.chatId = chatId
        getChatMember.userId = userId
        return execute(getChatMember)
    }

    /**
     * Извлекает ID бота из токена
     */
    fun getBotUserId(): Long {
        return botConfig.token.split(":")[0].toLong()
    }

    /**
     * Отправляет текстовое сообщение в канал или чат
     */
    @Throws(TelegramApiException::class)
    fun sendMessageToChannel(chatId: String, text: String): Message {
        val sendMessage = SendMessage()
        sendMessage.chatId = chatId
        sendMessage.text = text
        sendMessage.enableMarkdown(true) // Включаем поддержку Markdown
        return execute(sendMessage)
    }

    /**
     * Отправляет документ (файл) пользователю
     */
    @Throws(TelegramApiException::class)
    fun sendDocument(chatId: Long, fileBytes: ByteArray, fileName: String, caption: String? = null): Message {
        val sendDocument = SendDocument()
        sendDocument.chatId = chatId.toString()
        sendDocument.document = InputFile(ByteArrayInputStream(fileBytes), fileName)

        if (caption != null) {
            sendDocument.caption = caption
        }

        return execute(sendDocument)
    }
}