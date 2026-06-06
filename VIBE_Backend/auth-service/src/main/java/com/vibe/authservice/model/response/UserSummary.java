package com.vibe.authservice.model.response;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
public class UserSummary {
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private String profilePictureUrl;
    private String preferredLanguage;
    private int streakDays;
}
