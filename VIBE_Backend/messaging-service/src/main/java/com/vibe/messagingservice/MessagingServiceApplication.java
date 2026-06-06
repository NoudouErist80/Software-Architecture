package com.vibe.messagingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VIBE Messaging Service
 * - Real-time messaging via WebSocket (STOMP)
 * - AI-powered chat summaries (Claude API)
 * - Multi-language message translation
 * - Stores conversations and messages in MongoDB
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.vibe.messagingservice", "com.vibe.common"})
@EnableAsync
@EnableScheduling
public class MessagingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessagingServiceApplication.class, args);
    }
}
