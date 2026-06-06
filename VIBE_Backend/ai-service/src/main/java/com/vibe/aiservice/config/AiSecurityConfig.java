package com.vibe.aiservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the VIBE AI Service.
 *
 * <h3>Root cause of the 401 Unauthorized on /ai/translate</h3>
 *
 * The API Gateway's {@code JwtAuthFilter} validates the JWT, injects
 * {@code X-User-Id / X-Username / X-User-Role} headers, then <b>strips</b>
 * the {@code Authorization: Bearer} header before forwarding the request to
 * downstream services.
 *
 * <p>The original {@code AiSecurityConfig} used Spring's
 * {@code oauth2ResourceServer} which requires the {@code Authorization: Bearer}
 * header to be present.  Because the gateway removes it, every request from
 * the frontend arrived at the AI service without a Bearer token → the resource
 * server rejected all requests with 401.
 *
 * <h3>Fix — follow the same pattern as messaging-service</h3>
 *
 * The messaging service's security config uses {@code .anyRequest().permitAll()}
 * and trusts the gateway-injected {@code X-User-Id} header for identity.
 * This is the correct microservice pattern when the gateway handles authentication:
 *
 * <ul>
 *   <li>Gateway validates JWT once for all services (single responsibility).</li>
 *   <li>Downstream services trust {@code X-User-Id} (set only by the gateway).</li>
 *   <li>No double-validation overhead — critical for billion-user throughput.</li>
 * </ul>
 *
 * <h3>Production hardening</h3>
 * For production, add network-level protection (mTLS or IP allowlisting)
 * so only the gateway can reach the AI service.  This prevents anyone from
 * spoofing {@code X-User-Id} by calling the AI service directly.
 *
 * The AI service controller already enforces its own business-level access
 * control (e.g. conversation participant validation in the messaging service
 * before proxying AI calls).
 */
@Configuration
@EnableWebSecurity
public class AiSecurityConfig {

    @Value("${vibe.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain aiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless — no server-side session; scales horizontally
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF is irrelevant for stateless REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            // CORS — locked to configured frontend origins
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            .authorizeHttpRequests(auth -> auth
                // Health / readiness probes — no auth required
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/actuator/info",
                    "/actuator/**"
                ).permitAll()

                // Swagger / OpenAPI docs — open during development
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // All AI endpoints — authentication is enforced at the API Gateway level.
                // The gateway validates the JWT and injects X-User-Id before forwarding.
                // No re-validation needed here; that would add latency and double the
                // cryptographic work for every AI call (same pattern as messaging-service).
                .anyRequest().permitAll()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        config.setAllowedOrigins(origins);

        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Request-ID",
            "X-Correlation-ID", "X-User-Id", "X-Username", "X-User-Role",
            "Accept", "Origin"
        ));
        config.setExposedHeaders(Arrays.asList("X-Request-ID", "X-Correlation-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}   