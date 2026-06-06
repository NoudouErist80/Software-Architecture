package com.vibe.notificationservice.kafka;

import com.vibe.common.constants.VibeConstants;
import com.vibe.common.enums.NotificationType;
import com.vibe.common.event.TokenEarnedEvent;
import com.vibe.notificationservice.model.entity.Notification;
import com.vibe.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationRepository notificationRepository;

    @KafkaListener(topics = VibeConstants.TOPIC_TOKEN_EARNED,
                   groupId = VibeConstants.GROUP_NOTIFICATION_SERVICE)
    public void onTokenEarned(TokenEarnedEvent event) {
        Notification notification = Notification.builder()
                .recipientId(event.getUserId())
                .type(NotificationType.TOKEN_EARNED)
                .title("You earned tokens! 🪙")
                .body("+" + event.getTokensEarned() + " VIBE tokens added to your wallet — " +
                      event.getEarnType().name().toLowerCase().replace("_", " "))
                .referenceId(event.getReferenceId())
                .createdAt(Instant.now())
                .build();
        notificationRepository.save(notification);
        log.debug("Token earned notification created for user {}", event.getUserId());
    }

    @KafkaListener(topics = VibeConstants.TOPIC_USER_REGISTERED,
                   groupId = VibeConstants.GROUP_NOTIFICATION_SERVICE)
    public void onUserRegistered(java.util.Map<String, Object> event) {
        String userId = (String) event.get("userId");
        String username = (String) event.get("username");
        Notification notification = Notification.builder()
                .recipientId(userId)
                .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                .title("Welcome to VIBE, " + username + "! 🎉")
                .body("Your world. Your words. Your wallet. Start earning tokens just by using the app!")
                .createdAt(Instant.now())
                .build();
        notificationRepository.save(notification);
    }
}
