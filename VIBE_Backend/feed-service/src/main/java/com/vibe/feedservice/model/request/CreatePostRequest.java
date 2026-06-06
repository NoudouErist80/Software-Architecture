package com.vibe.feedservice.model.request;

import com.vibe.feedservice.model.entity.enums.MoodTag;
import com.vibe.feedservice.model.entity.enums.PostType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreatePostRequest {
    @NotNull
    private PostType type;
    private String caption;
    private String mediaUrl;
    private String thumbnailUrl;
    private int durationSeconds;
    private List<MoodTag> moodTags;
    private List<String> hashtags;
    private String location;
}
