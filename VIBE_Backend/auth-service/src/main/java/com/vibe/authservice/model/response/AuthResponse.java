package com.vibe.authservice.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long accessTokenExpiresIn;
    private UserSummary user;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserSummary {
        private String id;
        private String username;
        private String fullName;
        private String email;
        private String phoneNumber;
        private String role;
        private String profilePictureUrl;
        private String preferredLanguage;
        private int streakDays;
        private String countryCode;
    }
}
