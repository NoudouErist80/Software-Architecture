package com.vibe.common.event;
import com.vibe.common.enums.TokenEarnType;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TokenEarnedEvent {
    private String userId;
    private int tokensEarned;
    private TokenEarnType earnType;
    private String referenceId;
}
