package com.vibe.aiservice.service;

import com.vibe.aiservice.model.request.MoodDetectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Detects the mood/emotion category of post content.
 * Used to auto-tag posts for the mood-aware feed.
 * Returns one of the MoodTag enum values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoodDetectionService {

    private final ClaudeApiService claudeApiService;

    public String detectMood(MoodDetectRequest request) {
        String prompt = String.format("""
            Analyse the following social media post content and classify its mood/energy.
            
            Return ONLY one word from this exact list (no other text):
            ENERGISED, RELAXED, HAPPY, THOUGHTFUL, DOWN, FUNNY, INSPIRING,
            EDUCATIONAL, ROMANTIC, SPORTY, MUSICAL, CULTURAL, TRAVEL, FOOD, FASHION, TECH
            
            Post content: "%s"
            
            Mood classification:
            """, request.getContent());

        String mood = claudeApiService.ask(prompt).trim().toUpperCase();

        // Validate it's one of our enum values
        try {
            return mood;
        } catch (Exception e) {
            return "HAPPY"; // default fallback
        }
    }
}
