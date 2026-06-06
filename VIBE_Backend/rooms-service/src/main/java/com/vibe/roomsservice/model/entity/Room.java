package com.vibe.roomsservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;

/**
 * Contextual Room — group audio/video space with a goal and optional deadline.
 * Rooms auto-vanish when deadline passes or when manually ended.
 */
@Document(collection = "rooms")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Room {
    @Id
    private String id;
    private String hostId;
    private String hostUsername;
    private String title;
    private String description;
    private String goal;                 // What this room is for (study/prayer/chill etc.)
    private String category;             // STUDY, PRAYER, BUSINESS, MUSIC, SPORTS, etc.
    private String language;             // Preferred room language code

    @Builder.Default
    private List<String> participantIds = new ArrayList<>();
    @Builder.Default
    private Map<String, String> participantRoles = new HashMap<>(); // userId -> SPEAKER/LISTENER

    private int maxParticipants;
    private boolean isLive;
    private boolean isPrivate;
    private boolean autoVanish;          // Vanish when deadline passes

    @Indexed(expireAfterSeconds = 0)     // TTL index driven by vanishAt
    private Instant vanishAt;

    private String streamKey;            // For live streaming
    private String recordingUrl;

    @Builder.Default
    private List<String> coHostIds = new ArrayList<>();

    @Builder.Default
    private int peakParticipantCount = 0;

    @Builder.Default
    private long totalMinutesHosted = 0L;

    @CreatedDate
    private Instant createdAt;
    private Instant endedAt;
}
