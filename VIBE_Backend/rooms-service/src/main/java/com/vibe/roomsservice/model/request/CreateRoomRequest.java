package com.vibe.roomsservice.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.Instant;

@Data
public class CreateRoomRequest {
    @NotBlank
    private String title;
    private String description;
    private String goal;
    private String category; // STUDY, PRAYER, BUSINESS, MUSIC, SPORTS, GENERAL
    private String language;
    private int maxParticipants;
    private boolean isPrivate;
    private Instant deadlineAt; // auto-vanish at this time
}
