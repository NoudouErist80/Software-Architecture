package com.vibe.authservice.kafka;

import com.vibe.authservice.model.entity.User;
import com.vibe.common.constants.VibeConstants;
import com.vibe.common.enums.TokenEarnType;
import com.vibe.common.event.TokenEarnedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VibeEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${vibe.kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Async("vibeTaskExecutor")
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishUserRegisteredFallback")
    @Retry(name = "kafkaPublisher")
    public void publishUserRegistered(User user) {
        if (!kafkaEnabled) return;
        Map<String, Object> event = Map.of(
                "userId", user.getId().toString(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "countryCode", user.getCountryCode() != null ? user.getCountryCode() : "CM",
                "occurredAt", Instant.now().toString()
        );
        kafkaTemplate.send(VibeConstants.TOPIC_USER_REGISTERED, user.getId().toString(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Kafka user.registered failed: {}", ex.getMessage());
                    else log.info("Published user.registered for {}", user.getUsername());
                });
    }

    public void publishUserRegisteredFallback(User user, Throwable t) {
        log.warn("CB OPEN — user.registered dropped for {} ({}): {}",
                user.getUsername(), user.getId(), t.getMessage());
    }

    @Async("vibeTaskExecutor")
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishStreakFallback")
    @Retry(name = "kafkaPublisher")
    public void publishStreakEarned(User user, int streakDays) {
        if (!kafkaEnabled) return;
        int bonus = streakDays >= 30 ? VibeConstants.TOKEN_EARN_STREAK_DAY_30
                  : streakDays >= 7  ? VibeConstants.TOKEN_EARN_STREAK_DAY_7
                  : VibeConstants.TOKEN_EARN_STREAK_DAY_1;
        TokenEarnedEvent event = TokenEarnedEvent.builder()
                .userId(user.getId().toString())
                .tokensEarned(bonus)
                .earnType(TokenEarnType.DAILY_STREAK)
                .referenceId("streak-day-" + streakDays)
                .build();
        kafkaTemplate.send(VibeConstants.TOPIC_TOKEN_EARNED, user.getId().toString(), event);
    }

    public void publishStreakFallback(User user, int days, Throwable t) {
        log.warn("CB OPEN — streak event dropped for {}: {}", user.getUsername(), t.getMessage());
    }
}
