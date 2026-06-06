package com.vibe.messagingservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PresenceService} — Redis-backed online/last-seen tracking
 * and the broadcast of presence changes to /topic/presence.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SimpMessagingTemplate ws;
    @InjectMocks PresenceService presence;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void userConnected_whenPreviouslyOffline_setsKeyAndBroadcasts() {
        when(redis.hasKey(anyString())).thenReturn(false);

        presence.userConnected("u1");

        verify(valueOps).set(contains("u1"), eq("online"), anyLong(), eq(TimeUnit.SECONDS));
        verify(ws).convertAndSend(eq("/topic/presence"), any(Object.class));
    }

    @Test
    void userConnected_whenAlreadyOnline_doesNotBroadcastAgain() {
        when(redis.hasKey(anyString())).thenReturn(true);

        presence.userConnected("u1");

        verify(ws, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void userDisconnected_recordsLastSeenAndBroadcastsOffline() {
        presence.userDisconnected("u1");

        verify(redis).delete(contains("u1"));
        verify(valueOps).set(contains("lastseen"), anyString());
        verify(ws).convertAndSend(eq("/topic/presence"), any(Object.class));
    }

    @Test
    void isOnline_reflectsRedisKeyPresence() {
        when(redis.hasKey(anyString())).thenReturn(true);
        assertThat(presence.isOnline("u1")).isTrue();
    }

    @Test
    void getPresenceInfo_returnsOnline() {
        when(redis.hasKey(anyString())).thenReturn(true);
        assertThat(presence.getPresenceInfo("u1")).containsEntry("status", "online");
    }

    @Test
    void getPresenceInfo_returnsOfflineWithLastSeen() {
        when(redis.hasKey(anyString())).thenReturn(false);
        when(valueOps.get(contains("lastseen"))).thenReturn("2026-06-05T10:00:00Z");

        Map<String, String> info = presence.getPresenceInfo("u1");

        assertThat(info).containsEntry("status", "offline");
        assertThat(info).containsEntry("lastSeen", "2026-06-05T10:00:00Z");
    }

    @Test
    void heartbeat_refreshesTtl() {
        presence.heartbeat("u1");
        verify(redis).expire(contains("u1"), anyLong(), eq(TimeUnit.SECONDS));
    }
}
