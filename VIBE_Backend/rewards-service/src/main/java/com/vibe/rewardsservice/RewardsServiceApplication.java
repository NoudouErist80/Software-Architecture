package com.vibe.rewardsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VIBE Rewards Service — The Token Economy Engine
 *
 * This service is the financial heart of VIBE.
 * It listens to Kafka events from all other services and credits tokens
 * to users for every genuine activity. It also handles:
 * - Wallet management (balance, history)
 * - Token redemption (Mobile Money, airtime, Premium)
 * - Leaderboard calculations
 * - Daily/weekly streak bonuses
 * - Anti-fraud detection
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication(scanBasePackages = {"com.vibe.rewardsservice", "com.vibe.common"})
@EnableCaching @EnableKafka @EnableAsync @EnableScheduling
public class RewardsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RewardsServiceApplication.class, args);
    }
}
