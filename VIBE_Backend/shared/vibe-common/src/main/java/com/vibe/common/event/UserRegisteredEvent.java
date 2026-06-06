package com.vibe.common.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserRegisteredEvent {
    private String userId;
    private String username;
    private String email;
    private String countryCode;
    private Instant occurredAt;
}
