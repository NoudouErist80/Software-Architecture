package com.vibe.common.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatusPostedEvent {
    private String userId;
    private String statusId;
    private String type; // TEXT, IMAGE, VIDEO
    private Instant occurredAt;
}
