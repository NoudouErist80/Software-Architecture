package com.vibe.common.event;

import com.vibe.common.enums.NotificationType;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationEvent {
    private String recipientId;
    private String actorId;
    private String actorUsername;
    private NotificationType type;
    private String title;
    private String body;
    private String referenceId;
    private String referenceType; // POST, MESSAGE, ROOM, STATUS
}
