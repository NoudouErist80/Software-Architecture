package com.vibe.messagingservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security config for the messaging service.
 *
 * <p>JWT validation happens at the API Gateway level — the gateway verifies
 * the token and injects X-User-Id / X-Username headers into every forwarded
 * request.  The messaging service trusts these headers and does NOT re-validate
 * the JWT (no double-validation overhead).
 *
 * <p>WebSocket STOMP authentication is handled separately by
 * {@link WebSocketAuthInterceptor} on the CONNECT frame — allowing real-time
 * connections to carry the JWT without relying on HTTP headers.
 *
 * <p>For production: add IP-allowlisting or mTLS to ensure only the gateway
 * can reach the messaging service directly (preventing header spoofing).
 */
@Configuration
@EnableWebSecurity
public class MessagingSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> {})  // CORS configured per-endpoint or at gateway level
            .authorizeHttpRequests(auth -> auth
                // WebSocket SockJS handshake endpoints
                .requestMatchers("/ws/messaging/**").permitAll()
                // Actuator health checks
                .requestMatchers("/actuator/**").permitAll()
                // API docs
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // All messaging REST endpoints require the gateway X-User-Id header
                // (enforced at service level by @RequestHeader("X-User-Id") in controllers)
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
