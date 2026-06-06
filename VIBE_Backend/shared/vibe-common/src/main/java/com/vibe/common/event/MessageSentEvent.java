package com.vibe.common.event;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MessageSentEvent {
    private String senderId;
    private String conversationId;
    private String messageId;
    private boolean isGroupMessage;
}
