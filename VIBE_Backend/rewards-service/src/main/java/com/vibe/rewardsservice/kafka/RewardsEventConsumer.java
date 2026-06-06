package com.vibe.rewardsservice.kafka;

import com.vibe.common.constants.VibeConstants;
import com.vibe.common.enums.TokenEarnType;
import com.vibe.common.event.TokenEarnedEvent;
import com.vibe.rewardsservice.service.RewardsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardsEventConsumer {

    private final RewardsService rewardsService;

    @KafkaListener(topics = VibeConstants.TOPIC_TOKEN_EARNED,
                   groupId = VibeConstants.GROUP_REWARDS_SERVICE)
    public void onTokenEarned(TokenEarnedEvent event) {
        try {
            rewardsService.creditTokens(
                    event.getUserId(),
                    event.getTokensEarned(),
                    event.getEarnType(),
                    event.getReferenceId());
        } catch (Exception e) {
            log.error("Failed to credit tokens for user {}: {}", event.getUserId(), e.getMessage());
        }
    }

    @KafkaListener(topics = VibeConstants.TOPIC_VIDEO_WATCHED,
                   groupId = VibeConstants.GROUP_REWARDS_SERVICE)
    public void onVideoWatched(@org.springframework.messaging.handler.annotation.Payload java.util.Map<String, Object> event,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            String viewerId = (String) event.get("viewerId");
            String creatorId = (String) event.get("creatorId");
            int watchedSeconds = ((Number) event.get("watchedSeconds")).intValue();

            if (watchedSeconds >= 30) {
                // Viewer earns tokens for watching
                rewardsService.creditTokens(viewerId, VibeConstants.TOKEN_EARN_WATCH_VIDEO,
                        TokenEarnType.WATCH_VIDEO, (String) event.get("postId"));
            }
        } catch (Exception e) {
            log.error("Failed to process video.watched event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = VibeConstants.TOPIC_MESSAGE_SENT,
                   groupId = VibeConstants.GROUP_REWARDS_SERVICE)
    public void onMessageSent(@org.springframework.messaging.handler.annotation.Payload java.util.Map<String, Object> event) {
        try {
            String senderId = (String) event.get("senderId");
            rewardsService.creditTokens(senderId, VibeConstants.TOKEN_EARN_SEND_MESSAGE,
                    TokenEarnType.SEND_MESSAGE, (String) event.get("messageId"));
        } catch (Exception e) {
            log.error("Failed to process message.sent reward: {}", e.getMessage());
        }
    }
}
