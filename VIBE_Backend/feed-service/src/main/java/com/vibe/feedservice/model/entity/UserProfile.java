package com.vibe.feedservice.model.entity;

import com.vibe.feedservice.model.entity.enums.MoodTag;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;

@Document(collection = "user_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserProfile {
    @Id
    private String id;
    @Indexed(unique = true)
    private String userId;
    private String username;
    private String fullName;
    private String bio;
    private String profilePictureUrl;
    private String coverPictureUrl;
    private MoodTag currentMood;
    @Builder.Default
    private int followerCount = 0;
    @Builder.Default
    private int followingCount = 0;
    @Builder.Default
    private int postCount = 0;
    @Builder.Default
    private List<String> followerIds = new ArrayList<>();
    @Builder.Default
    private List<String> followingIds = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
}
