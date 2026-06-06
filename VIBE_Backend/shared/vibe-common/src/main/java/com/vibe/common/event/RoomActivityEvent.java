package com.vibe.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomActivityEvent {
    private String userId;
    private String roomId;
    private String hostId;
    private int participantCount;
    private boolean isHost;
    private int durationMinutes;
    @Builder.Default
    private Instant occurredAt = Instant.now();
}
