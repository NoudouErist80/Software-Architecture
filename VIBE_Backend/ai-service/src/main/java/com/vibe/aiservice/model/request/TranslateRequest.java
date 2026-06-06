package com.vibe.aiservice.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TranslateRequest {
    @NotBlank
    @Size(max = 5000, message = "Text too long for translation")
    private String text;

    @NotBlank
    private String sourceLanguage;

    @NotBlank
    private String targetLanguage;

    private String contentType = "message"; // "message", "caption", "voice_note"
}
