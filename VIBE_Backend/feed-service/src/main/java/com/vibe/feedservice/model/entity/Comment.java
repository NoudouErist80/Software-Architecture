package com.vibe.feedservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;

@Document(collection = "comments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Comment {
    @Id
    private String id;
    @Indexed
    private String postId;
    private String authorId;
    private String authorUsername;
    private String content;
    @Builder.Default
    private Set<String> likes = new HashSet<>();
    @CreatedDate
    private Instant createdAt;
}
