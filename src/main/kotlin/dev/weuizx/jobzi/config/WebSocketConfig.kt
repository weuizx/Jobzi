package dev.weuizx.jobzi.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * WebSocket configuration for real-time Telegram authentication updates.
 *
 * Endpoints:
 * - /ws - WebSocket connection with SockJS fallback
 *
 * Topics:
 * - /topic/telegram-auth/{accountId} - Authentication updates for specific account
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // Enable simple broker for broadcasting messages
        registry.enableSimpleBroker("/topic")

        // Set prefix for messages from client
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // Register STOMP endpoint with SockJS fallback
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // Configure properly for production
            .withSockJS()
    }
}
