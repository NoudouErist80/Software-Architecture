package com.vibe.aiservice.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class SummariseRequest {
    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    @NotEmpty(message = "Messages list cannot be empty")
    private List<MessageEntry> messages;

    private String targetLanguage = "en";
    private String conversationName;

    @Data
    public static class MessageEntry {
        private String senderUsername;
        private String content;
        private String timestamp;
    }
}
