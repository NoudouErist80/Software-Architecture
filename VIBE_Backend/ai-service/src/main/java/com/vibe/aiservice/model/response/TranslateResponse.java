package com.vibe.aiservice.model.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class TranslateResponse {
    private String originalText;
    private String translatedText;
    private String sourceLanguage;
    private String targetLanguage;
    private boolean cached;
}
