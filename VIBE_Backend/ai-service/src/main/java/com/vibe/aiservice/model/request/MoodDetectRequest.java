package com.vibe.aiservice.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MoodDetectRequest {
    @NotBlank
    private String content;
    private String mediaType = "text";
}
