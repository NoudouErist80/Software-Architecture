package com.vibe.aiservice.service;

import com.vibe.aiservice.model.request.SummariseRequest;
import com.vibe.aiservice.model.response.SummariseResponse;
import com.vibe.common.constants.VibeConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * VIBE #1 Innovation Feature: AI Group Chat Summariser
 *
 * Supports all 9 languages:
 * English, French, Hausa, Ewondo, Pidgin English,
 * Yoruba, Twi (Ghana), Fante (Ghana), Ga (Ghana)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummarisationService {

    private final ClaudeApiService claudeApiService;

    @Cacheable(value = "chat-summaries", key = "#request.conversationId + '-' + #request.targetLanguage")
    public SummariseResponse summarise(SummariseRequest request) {
        String messagesText = request.getMessages().stream()
                .map(m -> String.format("[%s] %s: %s",
                        m.getTimestamp(), m.getSenderUsername(), m.getContent()))
                .collect(Collectors.joining("\n"));

        String languageInstruction = getLanguageInstruction(request.getTargetLanguage());

        String prompt = String.format("""
            You are the VIBE AI assistant helping a user catch up on messages they missed.
            
            %s
            
            Here are the recent messages from the group chat "%s":
            
            %s
            
            Please provide a concise summary using bullet points (maximum 5 bullets) covering:
            • The main topics discussed
            • Any important decisions, plans, or events mentioned
            • Anything the user should reply to or take action on
            
            Keep it friendly and conversational. Start directly with the bullet points.
            Do not include timestamps in your summary.
            """,
                languageInstruction,
                request.getConversationName() != null ? request.getConversationName() : "the group",
                messagesText
        );

        String summary = claudeApiService.ask(prompt);

        log.info("Summary generated for conversation {} in language {}",
                request.getConversationId(), request.getTargetLanguage());

        return SummariseResponse.builder()
                .conversationId(request.getConversationId())
                .summary(summary)
                .language(request.getTargetLanguage())
                .messagesAnalysed(request.getMessages().size())
                .generatedAt(Instant.now())
                .build();
    }

    private String getLanguageInstruction(String langCode) {
        return switch (langCode) {
            case VibeConstants.LANG_HAUSA  ->
                "Respond in Hausa language. Hausa is spoken widely in Nigeria, Niger, and Cameroon.";
            case VibeConstants.LANG_EWONDO ->
                "Respond in Ewondo language, a Bantu language spoken in Cameroon.";
            case VibeConstants.LANG_PIDGIN ->
                "Respond in Nigerian/Cameroonian Pidgin English (e.g. 'wetin dey happen', 'e don do').";
            case VibeConstants.LANG_YORUBA ->
                "Respond in Yoruba language as spoken in Nigeria.";
            case VibeConstants.LANG_TWI    ->
                "Respond in Twi (Akan) language as spoken in Ghana.";
            case VibeConstants.LANG_FANTE  ->
                "Respond in Fante language as spoken in Ghana.";
            case VibeConstants.LANG_GA     ->
                "Respond in Ga language as spoken in Accra, Ghana.";
            case VibeConstants.LANG_FRENCH ->
                "Respond in French (français).";
            default ->
                "Respond in English.";
        };
    }
}
