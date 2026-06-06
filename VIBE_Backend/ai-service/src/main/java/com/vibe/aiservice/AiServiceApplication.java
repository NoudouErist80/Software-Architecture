package com.vibe.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * VIBE AI Service — Intelligence Layer
 *
 * Powers all AI features across the VIBE platform:
 *
 * 1. GROUP CHAT SUMMARISER — "Catch me up" button
 *    Generates bullet-point summaries of missed group messages
 *    in any language: English, French, Hausa, Ewondo, Pidgin,
 *    Yoruba, Twi, Fante, Ga
 *
 * 2. REAL-TIME TRANSLATION — break language barriers
 *    Translates text messages, video captions, voice notes
 *    across all 9 supported African and international languages
 *
 * 3. MOOD DETECTION — feed personalisation
 *    Analyses post content to auto-tag mood categories
 *    (RELAXED, ENERGISED, FUNNY, INSPIRING, etc.)
 *
 * 4. CONTENT MODERATION — safety (future)
 *    Detects harmful content before it reaches users
 *
 * All AI calls powered by Claude API (Anthropic)
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication(scanBasePackages = {"com.vibe.aiservice", "com.vibe.common"})
@EnableCaching
@EnableKafka
@EnableAsync
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
