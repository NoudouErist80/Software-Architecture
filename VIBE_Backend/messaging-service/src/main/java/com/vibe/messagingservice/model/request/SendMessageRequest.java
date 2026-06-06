package com.vibe.messagingservice.model.request;

import com.vibe.messagingservice.model.entity.enums.MessageType;
import lombok.Data;

@Data
public class SendMessageRequest {
    private String conversationId;   // set by controller from path variable
    private MessageType type;
    private String content;
    private String mediaUrl;
    private String thumbnailUrl;
    private String replyToMessageId;
    private Boolean isForwarded;
    private String forwardedFromMessageId;
}
