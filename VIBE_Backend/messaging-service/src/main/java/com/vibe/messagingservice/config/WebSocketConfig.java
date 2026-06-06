package com.vibe.messagingservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-SockJS WebSocket configuration for the VIBE messaging service.
 *
 * <h3>Endpoint routing</h3>
 * <pre>
 *   Frontend (port 3000)
 *     └─ SockJS connects to: http://localhost:8090/ws/messaging
 *
 *   API Gateway (port 8090)
 *     └─ Route: Path=/ws/messaging/**  → http://localhost:8082
 *
 *   Messaging Service (port 8082)
 *     └─ SockJS endpoint: /ws/messaging
 * </pre>
 *
 * <h3>Authentication</h3>
 * JWT is validated in {@link WebSocketAuthInterceptor} on the CONNECT frame.
 * The Principal is then available in all @MessageMapping methods.
 *
 * <h3>Broker destinations</h3>
 * <ul>
 *   <li>{@code /topic/*}  — broadcast to all subscribers (group / conversation channels)</li>
 *   <li>{@code /queue/*}  — point-to-point delivery</li>
 *   <li>{@code /user/*}   — user-specific destinations (Spring resolves per-session queues)</li>
 *   <li>{@code /app/*}    — client sends directed here; Spring routes to @MessageMapping</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker for topic (broadcast) and user-specific queues
        config.enableSimpleBroker("/topic", "/queue", "/user");
        // All @MessageMapping methods are prefixed with /app
        config.setApplicationDestinationPrefixes("/app");
        // Spring converts /user/<userId>/queue/X to per-session queues
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws/messaging")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Register the JWT auth interceptor on the inbound channel.
     * This is the correct hook for STOMP-level authentication — it runs
     * before the message is dispatched to any @MessageMapping handler.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
