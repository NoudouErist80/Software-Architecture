package com.vibe.aiservice.controller;

import com.vibe.aiservice.service.AiService;
import com.vibe.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * VIBE AI REST Controller.
 *
 * <h3>Field-mapping fix (frontend ↔ backend alignment)</h3>
 *
 * The frontend {@code aiAPI.translate()} sends:
 * <pre>{ "text": "...", "toLang": "fr", "fromLang": "auto" }</pre>
 *
 * The controller now accepts BOTH {@code toLang} and {@code targetLanguage}
 * (with {@code toLang} taking precedence) so it is compatible with both the
 * updated {@code api.js} and any legacy clients.
 *
 * The {@code /summarise} endpoint now accepts a {@code text} field directly
 * (pre-built transcript from the frontend) in addition to the original fields,
 * removing the need for the AI service to call back to the messaging service.
 *
 * <h3>Security</h3>
 * Authentication is enforced by {@code AiSecurityConfig} — this controller
 * itself has no security annotations, keeping it clean (single responsibility).
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "VIBE AI — translate, summarise, detect language, generate captions")
public class AiController {

    private final AiService aiService;

    // ─── Translation ───────────────────────────────────────────────────────────

    /**
     * Translate text to any of 11 supported African + global languages.
     *
     * Accepted request body fields:
     * <ul>
     *   <li>{@code text}           — source text (required)</li>
     *   <li>{@code toLang}         — target language ISO code (preferred)</li>
     *   <li>{@code targetLanguage} — alias for toLang (legacy / alternative)</li>
     *   <li>{@code fromLang}       — source language code or "auto" (optional)</li>
     * </ul>
     *
     * Response: {@code ApiResponse<{ original, translated, targetLang }>}
     */
    @PostMapping("/translate")
    @Operation(summary = "Translate text to any of 11 supported African languages")
    public ResponseEntity<ApiResponse<Object>> translate(@RequestBody Map<String, String> request) {
        String text     = request.get("text");
        // Accept both field names — toLang wins if both present
        String toLang   = request.containsKey("toLang")
                ? request.get("toLang")
                : request.getOrDefault("targetLanguage", "en");
        String fromLang = request.getOrDefault("fromLang", "auto");

        String translated = aiService.translate(text, fromLang, toLang);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "original",     text != null ? text : "",
                "translated",   translated,
                "translatedText", translated,   // keep legacy key so old clients still work
                "targetLang",   toLang
        )));
    }

    // ─── Language detection ────────────────────────────────────────────────────

    @PostMapping("/detect-language")
    @Operation(summary = "Detect the language of a given text")
    public ResponseEntity<ApiResponse<Object>> detectLanguage(@RequestBody Map<String, String> request) {
        String lang = aiService.detectLanguage(request.get("text"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("detectedLanguage", lang)));
    }

    // ─── Summarisation ─────────────────────────────────────────────────────────

    /**
     * Summarise a conversation transcript.
     *
     * Accepted request body fields:
     * <ul>
     *   <li>{@code text}           — pre-built transcript string (preferred — sent by frontend)</li>
     *   <li>{@code transcript}     — alias for text (used by messaging-service internal calls)</li>
     *   <li>{@code conversationId} — for logging / future async lookup (optional)</li>
     *   <li>{@code language}       — response language ISO code (default "en")</li>
     *   <li>{@code style}          — "BULLET" | "DAILY_STANDUP" (default "BULLET")</li>
     *   <li>{@code type}           — "UNREAD" | "CUSTOM" (informational; processing done by caller)</li>
     *   <li>{@code period}         — "TODAY" | "YESTERDAY" | "WEEK" etc. (informational)</li>
     * </ul>
     *
     * Response: {@code ApiResponse<{ summary }>}
     */
    @PostMapping(value = {"/summarize", "/summarise"})
    @Operation(summary = "Summarize a conversation transcript")
    public ResponseEntity<ApiResponse<Object>> summarize(@RequestBody Map<String, String> request) {
        // Accept both 'text' (from frontend) and 'transcript' (from messaging-service)
        String text = request.containsKey("text")
                ? request.get("text")
                : request.getOrDefault("transcript", "");

        String language = request.getOrDefault("language", "en");
        String style    = request.getOrDefault("style", "BULLET");

        if (text == null || text.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("summary", "📭 No messages to summarise.")));
        }

        String summary = aiService.summarize(text, language, style);
        return ResponseEntity.ok(ApiResponse.success(Map.of("summary", summary)));
    }

    // ─── Caption generation ────────────────────────────────────────────────────

    @PostMapping("/caption")
    @Operation(summary = "Generate social media captions for a post")
    public ResponseEntity<ApiResponse<Object>> generateCaption(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(
                aiService.generateCaption(
                        request.get("context"),
                        request.getOrDefault("language", "en"))));
    }

    // ─── Supported languages ───────────────────────────────────────────────────

    @GetMapping({"/languages", "/supported-languages"})
    @Operation(summary = "Get list of all supported languages")
    public ResponseEntity<ApiResponse<Object>> getSupportedLanguages() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "languages", List.of(
                        Map.of("code", "en",  "name", "English",       "flag", "🇬🇧"),
                        Map.of("code", "fr",  "name", "Français",       "flag", "🇫🇷"),
                        Map.of("code", "ha",  "name", "Hausa",          "flag", "🌍"),
                        Map.of("code", "ewo", "name", "Ewondo",         "flag", "🇨🇲"),
                        Map.of("code", "pcm", "name", "Pidgin English", "flag", "🌍"),
                        Map.of("code", "yo",  "name", "Yorùbá",         "flag", "🇳🇬"),
                        Map.of("code", "tw",  "name", "Twi",            "flag", "🇬🇭"),
                        Map.of("code", "fat", "name", "Fante",          "flag", "🇬🇭"),
                        Map.of("code", "gaa", "name", "Ga",             "flag", "🇬🇭"),
                        Map.of("code", "ln",  "name", "Lingála",        "flag", "🇨🇩"),
                        Map.of("code", "bm",  "name", "Bamanankan",     "flag", "🇲🇱")
                )
        )));
    }
}