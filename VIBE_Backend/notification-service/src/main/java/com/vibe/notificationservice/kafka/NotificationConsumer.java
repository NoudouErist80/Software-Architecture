package com.vibe.notificationservice.kafka;

import com.vibe.common.constants.VibeConstants;
import com.vibe.common.event.NotificationEvent;
import com.vibe.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = VibeConstants.TOPIC_NOTIFICATION_SEND,
                   groupId = VibeConstants.GROUP_NOTIFICATION_SERVICE)
    public void onNotification(NotificationEvent event) {
        try {
            notificationService.createAndSend(event);
        } catch (Exception e) {
            log.error("Failed to process notification event: {}", e.getMessage());
        }
    }
}
