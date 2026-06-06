package com.vibe.notificationservice.model.entity;

import com.vibe.common.enums.NotificationType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "notifications")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Notification {
    @Id
    private String id;
    @Indexed
    private String recipientId;
    private String actorId;
    private String actorUsername;
    private NotificationType type;
    private String title;
    private String body;
    private String referenceId;
    private String referenceType;
    @Builder.Default
    private boolean isRead = false;
    @CreatedDate
    private Instant createdAt;
}
