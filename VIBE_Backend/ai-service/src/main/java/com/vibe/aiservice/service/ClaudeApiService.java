package com.vibe.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibe.common.exception.VibeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Low-level Claude API client.
 * All AI features in VIBE route through this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeApiService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${vibe.ai.claude.api-key:${CLAUDE_API_KEY:}}")
    private String apiKey;

    @Value("${vibe.ai.claude.api-url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${vibe.ai.claude.model:claude-sonnet-4-20250514}")
    private String model;

    @Value("${vibe.ai.claude.max-tokens:1000}")
    private int maxTokens;

    /**
     * Send a prompt to Claude and return the text response.
     * This is the foundation of ALL AI features in VIBE.
     */
    public String ask(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Claude API error {}: {}", response.statusCode(), response.body());
                throw VibeException.badRequest("AI service temporarily unavailable");
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("content").get(0).path("text").asText();

        } catch (VibeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage());
            throw VibeException.badRequest("AI service error: " + e.getMessage());
        }
    }
}
