package com.vibe.feedservice.model.entity;

import com.vibe.feedservice.model.entity.enums.MoodTag;
import com.vibe.feedservice.model.entity.enums.PostType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;

@Document(collection = "posts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Post {
    @Id
    private String id;

    @Indexed
    private String authorId;
    private String authorUsername;
    private String authorProfilePictureUrl;

    private PostType type;
    private String caption;
    private String mediaUrl;
    private String thumbnailUrl;
    private int durationSeconds;

    @Builder.Default
    private List<MoodTag> moodTags = new ArrayList<>();

    @Builder.Default
    private List<String> hashtags = new ArrayList<>();

    private String location;

    @Builder.Default
    private Set<String> likes = new HashSet<>();

    @Builder.Default
    private int commentCount = 0;

    @Builder.Default
    private long viewCount = 0L;

    private boolean isActive;
    private boolean isViral;
    private Instant wentViralAt;

    @CreatedDate
    private Instant createdAt;
}
