package com.vibe.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    // Fall back to the CLAUDE_API_KEY env var (set from .env by start-messenger.sh)
    // when the explicit property isn't provided — without this fallback the key
    // was always blank and every AI call returned "not configured".
    @Value("${vibe.ai.api-key:${CLAUDE_API_KEY:}}")
    private String claudeApiKey;

    @Value("${vibe.ai.model:claude-sonnet-4-6}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("en",  "English"),
            Map.entry("fr",  "French"),
            Map.entry("ha",  "Hausa"),
            Map.entry("ewo", "Ewondo"),
            Map.entry("pcm", "Pidgin English"),
            Map.entry("yo",  "Yoruba"),
            Map.entry("tw",  "Twi"),
            Map.entry("fat", "Fante"),
            Map.entry("gaa", "Ga"),
            Map.entry("ln",  "Lingala"),
            Map.entry("bm",  "Bambara")
    );

    public String translate(String text, String fromLang, String toLang) {
        String targetName = LANGUAGES.getOrDefault(toLang, toLang);
        String prompt = "Translate the following text to " + targetName +
                        ". Preserve the tone and style. Return ONLY the translation:\n\n" + text;
        return callClaude(prompt);
    }

    public String detectLanguage(String text) {
        String codes = String.join(", ", LANGUAGES.keySet());
        String prompt = "Detect the language of the following text. " +
                        "Return ONLY the language code from this list: " + codes + "\n\nText: " + text;
        return callClaude(prompt).trim().toLowerCase();
    }

    public String summarize(String text, String language, String style) {
        String langName = LANGUAGES.getOrDefault(language, "English");
        String stylePrompt = "DAILY_STANDUP".equals(style)
                ? "Format as bullet points: key decisions, action items, next steps."
                : "Summarize in 3-5 friendly bullet points.";
        String prompt = "You are VIBE's AI assistant for African users. " + stylePrompt +
                        " Respond in " + langName + ".\n\nContent:\n" + text;
        return callClaude(prompt);
    }

    public Map<String, Object> generateCaption(String context, String language) {
        String langName = LANGUAGES.getOrDefault(language, "English");
        String prompt = "Generate 3 engaging social media captions in " + langName +
                        " for an African social media platform called VIBE. Context: " + context +
                        ". Return a JSON array with fields: text, hashtags. Return ONLY valid JSON.";
        String response = callClaude(prompt);
        return Map.of("suggestions", response);
    }

    public boolean isSafe(String content) {
        String prompt = "Is this content safe for a general social media platform? " +
                        "Reply ONLY with 'SAFE' or 'UNSAFE':\n\n" + content;
        String result = callClaude(prompt).trim().toUpperCase();
        return result.startsWith("SAFE");
    }

    @SuppressWarnings("unchecked")
    private String callClaude(String prompt) {
        if (claudeApiKey == null || claudeApiKey.isBlank()) {
            return "[AI service not configured — set CLAUDE_API_KEY]";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", claudeApiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 1000,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.anthropic.com/v1/messages",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage());
            return "[AI temporarily unavailable]";
        }
    }
}
