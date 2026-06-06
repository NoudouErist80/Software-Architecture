package com.vibe.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;

/**
 * VIBE API Gateway
 *
 * Single entry point for all VIBE microservices.
 * Handles:
 * - JWT authentication (extracts user from token, passes as X-User-Id, X-Username, X-User-Role headers)
 * - Request routing to correct microservice
 * - CORS handling
 * - WebSocket proxying for real-time services
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication(
    scanBasePackages = {"com.vibe.gateway", "com.vibe.common"},
    // Exclude default user details service — we use our own JWT filter
    exclude = {ReactiveUserDetailsServiceAutoConfiguration.class}
)
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
