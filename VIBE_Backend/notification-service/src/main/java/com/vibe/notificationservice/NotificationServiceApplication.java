package com.vibe.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * VIBE Notification Service
 *
 * Listens to ALL platform events via Kafka and delivers notifications:
 * - In-app notifications (stored in MongoDB, served via REST)
 * - Push notifications (Firebase FCM — future)
 * - SMS notifications for cashout confirmations
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication(scanBasePackages = {"com.vibe.notificationservice", "com.vibe.common"})
@EnableKafka
@EnableAsync
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
