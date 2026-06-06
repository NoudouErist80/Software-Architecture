package com.vibe.gateway.config;

import com.vibe.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {
    @Value("${vibe.jwt.secret:ViBe_Super_Secret_2026_Africa_TchangoNoudouJoseph}")
    private String secret;
    @Value("${vibe.jwt.access-token-expiry-ms:900000}")
    private long accessExpiry;
    @Value("${vibe.jwt.refresh-token-expiry-ms:604800000}")
    private long refreshExpiry;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(secret, accessExpiry, refreshExpiry);
    }
}
