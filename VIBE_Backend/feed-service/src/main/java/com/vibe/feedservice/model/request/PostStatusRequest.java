package com.vibe.feedservice.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class PostStatusRequest {
    @NotBlank
    private String type; // TEXT, IMAGE, VIDEO
    private String content;
    private String mediaUrl;
    private String thumbnailUrl;
    private int durationSeconds;
    private String visibility; // EVERYONE, CONTACTS, FOLLOWERS, EXCEPT
    private List<String> exceptUserIds;
}
