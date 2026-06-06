package com.vibe.gateway.config;

import com.vibe.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway security configuration.
 * The API Gateway is the single entry point — all JWT validation happens here.
 * Downstream services trust X-User-Id, X-Username, X-User-Role headers.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${vibe.jwt.secret}")
    private String jwtSecret;

    /**
     * Expose JwtUtil as a Spring bean so JwtAuthFilter can autowire it.
     * We manually construct it here because the shared library's @Component
     * annotation requires the full Spring Security context which conflicts
     * with WebFlux in the reactive gateway.
     */
    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret);
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // All auth happens in JwtAuthFilter — Spring Security itself is permissive here
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
