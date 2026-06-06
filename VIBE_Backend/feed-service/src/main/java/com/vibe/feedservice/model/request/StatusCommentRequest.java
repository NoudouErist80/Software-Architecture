package com.vibe.feedservice.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StatusCommentRequest {
    @NotBlank
    private String content;
    private boolean isPrivate; // if true, sends to DM inbox
}
