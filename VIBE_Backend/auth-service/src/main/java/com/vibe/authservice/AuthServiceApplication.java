package com.vibe.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VIBE Authentication Service
 *
 * Responsible for:
 * - User registration and login
 * - JWT access + refresh token management
 * - Password management (hashing, reset)
 * - Session management via Redis
 * - Publishes UserRegisteredEvent to Kafka for downstream services
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication(scanBasePackages = {"com.vibe.authservice", "com.vibe.common"})
@EnableCaching
@EnableKafka
@EnableAsync
@EnableScheduling
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
