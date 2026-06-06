package com.vibe.common.event;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VideoWatchedEvent {
    private String viewerId;
    private String postId;
    private String creatorId;
    private int watchedSeconds;
}
