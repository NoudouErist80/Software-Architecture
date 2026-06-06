package com.vibe.aiservice.service;

import com.vibe.aiservice.model.request.TranslateRequest;
import com.vibe.aiservice.model.response.TranslateResponse;
import com.vibe.common.constants.VibeConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Real-time message and content translation.
 * Supports 9 languages including African languages not covered
 * by any other social platform (Hausa, Ewondo, Pidgin, Yoruba, Twi, Fante, Ga).
 * This is VIBE's language inclusion innovation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final ClaudeApiService claudeApiService;

    @Cacheable(value = "translations",
               key = "#request.text.hashCode() + '-' + #request.targetLanguage")
    public TranslateResponse translate(TranslateRequest request) {
        // Don't translate if same language
        if (request.getSourceLanguage().equalsIgnoreCase(request.getTargetLanguage())) {
            return TranslateResponse.builder()
                    .originalText(request.getText())
                    .translatedText(request.getText())
                    .sourceLanguage(request.getSourceLanguage())
                    .targetLanguage(request.getTargetLanguage())
                    .cached(false)
                    .build();
        }

        String contentContext = switch (request.getContentType()) {
            case "caption"    -> "This is a video caption.";
            case "voice_note" -> "This is a transcribed voice note.";
            default           -> "This is a chat message.";
        };

        String prompt = String.format("""
            Translate the following text from %s to %s.
            %s
            Return ONLY the translated text — no explanations, no quotes, no extra text.
            Preserve the original tone, emotion, and informal style.
            If slang or idioms cannot be directly translated, use the closest natural equivalent.
            
            Text to translate:
            %s
            """,
                getLanguageName(request.getSourceLanguage()),
                getLanguageName(request.getTargetLanguage()),
                contentContext,
                request.getText()
        );

        String translated = claudeApiService.ask(prompt);

        log.debug("Translated {} chars from {} to {}",
                request.getText().length(), request.getSourceLanguage(), request.getTargetLanguage());

        return TranslateResponse.builder()
                .originalText(request.getText())
                .translatedText(translated.trim())
                .sourceLanguage(request.getSourceLanguage())
                .targetLanguage(request.getTargetLanguage())
                .cached(false)
                .build();
    }

    private String getLanguageName(String code) {
        return switch (code) {
            case VibeConstants.LANG_HAUSA  -> "Hausa";
            case VibeConstants.LANG_EWONDO -> "Ewondo";
            case VibeConstants.LANG_PIDGIN -> "Pidgin English";
            case VibeConstants.LANG_YORUBA -> "Yoruba";
            case VibeConstants.LANG_TWI    -> "Twi";
            case VibeConstants.LANG_FANTE  -> "Fante";
            case VibeConstants.LANG_GA     -> "Ga";
            case VibeConstants.LANG_FRENCH -> "French";
            default                        -> "English";
        };
    }
}
