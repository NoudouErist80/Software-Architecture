package com.vibe.notificationservice.service;

import com.vibe.common.enums.NotificationType;
import com.vibe.common.event.NotificationEvent;
import com.vibe.notificationservice.model.entity.Notification;
import com.vibe.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate websocket;

    public void createAndSend(NotificationEvent event) {
        Notification notification = Notification.builder()
                .recipientId(event.getRecipientId())
                .actorId(event.getActorId())
                .actorUsername(event.getActorUsername())
                .type(event.getType())
                .title(event.getTitle())
                .body(event.getBody())
                .referenceId(event.getReferenceId())
                .referenceType(event.getReferenceType())
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        notification = notificationRepository.save(notification);

        // Push via WebSocket to recipient
        try {
            websocket.convertAndSendToUser(
                    event.getRecipientId(),
                    "/queue/notifications",
                    notification);
        } catch (Exception e) {
            log.warn("WebSocket push failed for {}: {}", event.getRecipientId(), e.getMessage());
        }
        log.info("Notification sent to {}: {}", event.getRecipientId(), event.getTitle());
    }

    public Page<Notification> getNotifications(String userId, int page, int size) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, Math.min(size, 50)));
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    public void markAllRead(String userId) {
        notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 100)).forEach(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    public void markRead(String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }
}
