package com.vibe.aiservice.model.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class SummariseResponse {
    private String conversationId;
    private String summary;
    private String language;
    private int messagesAnalysed;
    private Instant generatedAt;
}
