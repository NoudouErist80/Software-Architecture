package com.vibe.roomsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VIBE Rooms Service — Contextual Live Spaces
 *
 * Handles:
 * - Creating purpose-driven live group spaces (Rooms)
 * - Real-time participant tracking via Redis
 * - Auto-dissolve rooms when their purpose is fulfilled
 * - WebSocket push for room events (join, leave, react)
 * - Token rewards for hosts and participants via Kafka
 * - Sponsored room management for brand partnerships
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication(scanBasePackages = {"com.vibe.roomsservice", "com.vibe.common"})
@EnableCaching
@EnableKafka
@EnableAsync
@EnableScheduling
public class RoomsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoomsServiceApplication.class, args);
    }
}
