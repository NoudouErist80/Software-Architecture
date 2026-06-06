package com.vibe.roomsservice.model.response;

import com.vibe.roomsservice.model.entity.enums.RoomStatus;
import com.vibe.roomsservice.model.entity.enums.RoomType;
import com.vibe.roomsservice.model.entity.enums.RoomVisibility;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class RoomResponse {
    private String id;
    private String title;
    private String description;
    private String coverEmoji;
    private String hostId;
    private String hostUsername;
    private RoomType type;
    private RoomVisibility visibility;
    private RoomStatus status;
    private int participantCount;
    private int maxParticipants;
    private boolean isSponsored;
    private String sponsorName;
    private String sponsorLogoUrl;
    private String dissolveCondition;
    private Instant scheduledDissolveAt;
    private Instant scheduledStartAt;
    private Instant createdAt;
    private String joinCode;
}
