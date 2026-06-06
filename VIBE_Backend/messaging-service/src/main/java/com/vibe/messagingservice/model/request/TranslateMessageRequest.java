package com.vibe.messagingservice.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TranslateMessageRequest {
    @NotBlank
    private String targetLanguage; // ISO 639-1 code: "en", "fr", "sw", "ha", etc.
}
