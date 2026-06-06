package com.vibe.feedservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VIBE Feed Service — Mood-Aware Content Discovery
 *
 * Handles:
 * - Post creation (text, image, video)
 * - Mood-aware feed generation (filters content by user emotional state)
 * - Video watching events → Kafka → token rewards
 * - Post engagement (likes, comments, shares)
 * - Viral post detection (10K+ views in 24h → bonus tokens)
 * - User profile management
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.vibe.feedservice", "com.vibe.common"})
@EnableScheduling
@EnableCaching
@EnableKafka
@EnableAsync
public class FeedServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeedServiceApplication.class, args);
    }
}