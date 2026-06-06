package com.vibe.feedservice.model.request;

import lombok.Data;

@Data
public class WatchVideoRequest {
    private String postId;
    private int watchedSeconds;
}
