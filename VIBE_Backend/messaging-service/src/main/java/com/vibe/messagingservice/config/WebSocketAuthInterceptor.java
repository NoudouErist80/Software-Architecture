package com.vibe.messagingservice.config;

import com.vibe.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP channel interceptor that validates JWT tokens in CONNECT frames
 * and sets the Spring Security Principal for WebSocket sessions.
 *
 * <p>WHY THIS IS NEEDED: The API Gateway strips the Authorization header
 * before forwarding HTTP requests to downstream services. For WebSocket
 * connections, the STOMP CONNECT frame carries the JWT as a STOMP header
 * (not an HTTP header), so the gateway passes it through. This interceptor
 * reads that STOMP header and establishes the security context.
 *
 * <p>Without this, principal.getName() throws NullPointerException in
 * @MessageMapping methods because Spring cannot auto-resolve the Principal.
 *
 * <p>This follows the same pattern used by Discord, Slack, and WhatsApp Web
 * for WebSocket authentication in microservice architectures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // Only authenticate on CONNECT frames — the session keeps the Principal
        // for all subsequent SEND / SUBSCRIBE frames automatically via Spring's
        // SimpSessionScope.
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtUtil.validateToken(token)) {
                        String userId   = jwtUtil.extractUserId(token);
                        String username = jwtUtil.extractUsername(token);
                        String role     = jwtUtil.extractRole(token);

                        // Build a Spring Security Principal — userId as the principal name
                        // so principal.getName() returns the userId throughout message handlers.
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        userId,   // principal name = userId (UUID)
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                                );
                        // Store username as a credential for handlers that need it
                        auth.setDetails(username);

                        accessor.setUser(auth);
                        log.debug("[VIBE WS] Authenticated STOMP connection for user={} ({})", username, userId);
                    } else {
                        log.warn("[VIBE WS] Invalid JWT in STOMP CONNECT — rejecting");
                        return null; // Reject connection
                    }
                } catch (Exception e) {
                    log.warn("[VIBE WS] JWT validation failed in STOMP CONNECT: {}", e.getMessage());
                    return null; // Reject connection
                }
            } else {
                // Allow unauthenticated connections for SockJS info endpoint.
                // Message handlers that need authentication should check principal != null.
                log.debug("[VIBE WS] STOMP CONNECT without Authorization header — guest connection");
            }
        }

        return message;
    }
}
