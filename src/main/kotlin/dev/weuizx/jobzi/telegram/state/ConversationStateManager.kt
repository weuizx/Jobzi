package dev.weuizx.jobzi.telegram.state

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления состоянием диалогов пользователей
 * В production рекомендуется использовать Redis или базу данных
 */
@Component
class ConversationStateManager {

    private val userStates = ConcurrentHashMap<Long, ConversationState>()
    private val userContext = ConcurrentHashMap<Long, MutableMap<String, Any>>()

    /**
     * Получить текущее состояние пользователя
     */
    fun getState(userId: Long): ConversationState {
        return userStates.getOrDefault(userId, ConversationState.NONE)
    }

    /**
     * Установить состояние для пользователя
     */
    fun setState(userId: Long, state: ConversationState) {
        userStates[userId] = state
    }

    /**
     * Сбросить состояние пользователя
     */
    fun clearState(userId: Long) {
        userStates.remove(userId)
        userContext.remove(userId)
    }

    /**
     * Сохранить данные в контексте пользователя
     */
    fun setContextValue(userId: Long, key: String, value: Any) {
        val context = userContext.getOrPut(userId) { mutableMapOf() }
        context[key] = value
    }

    /**
     * Получить значение из контекста пользователя
     */
    fun <T> getContextValue(userId: Long, key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return userContext[userId]?.get(key) as? T
    }

    /**
     * Получить весь контекст пользователя
     */
    fun getContext(userId: Long): Map<String, Any> {
        return userContext[userId] ?: emptyMap()
    }

    /**
     * Удалить конкретное значение из контекста пользователя
     */
    fun clearContextValue(userId: Long, key: String) {
        userContext[userId]?.remove(key)
    }

    /**
     * Очистить весь контекст пользователя (но сохранить состояние)
     */
    fun clearContext(userId: Long) {
        userContext.remove(userId)
    }
}