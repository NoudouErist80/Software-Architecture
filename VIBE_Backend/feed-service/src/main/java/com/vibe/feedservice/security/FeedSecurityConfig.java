package com.vibe.feedservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the VIBE Feed Service — <b>gateway-trust model</b>.
 *
 * <p>The API Gateway is the single point of JWT validation. It verifies the
 * token and forwards the authenticated identity as trusted internal headers
 * ({@code X-User-Id}, {@code X-Username}, {@code X-User-Role}), and it
 * <em>strips the original {@code Authorization} header</em> so the raw JWT never
 * reaches this service. Feed controllers read identity straight from those
 * headers via {@code @RequestHeader("X-User-Id")}.
 *
 * <p><b>Why this replaced the previous version:</b> the old config registered a
 * JWT filter and used {@code .anyRequest().authenticated()}. But since the
 * gateway removes the {@code Authorization} header before forwarding, there was
 * no token left for that filter to read — so the SecurityContext was never
 * populated and <em>every</em> gateway-forwarded request was rejected with 401.
 * This version trusts the gateway, matching messaging-service, rooms-service,
 * notification-service and the rest of the platform.
 *
 * <p><b>Production hardening note:</b> because downstream services trust the
 * {@code X-User-Id} header without re-validation, the services must only be
 * reachable <em>via the gateway</em> (network isolation / IP-allowlisting /
 * mTLS) so a direct caller cannot spoof that header.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class FeedSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Health checks and API docs need no identity
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/v3/api-docs.yaml")
                        .permitAll()
                        // Everything else is reached only through the gateway,
                        // which already validated the JWT; identity arrives as
                        // X-User-Id / X-Username headers consumed by controllers.
                        .anyRequest().permitAll())
                .build();
    }
}
