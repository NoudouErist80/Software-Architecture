package com.vibe.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TokenCacheService} — verifies Redis usage on the happy
 * path and the transparent in-memory fallback when Redis is unavailable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenCacheServiceTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @InjectMocks TokenCacheService cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void set_writesToRedis() {
        cache.set("k", "v", Duration.ofMinutes(1));
        verify(valueOps).set("k", "v", Duration.ofMinutes(1));
    }

    @Test
    void get_returnsValueFromRedis() {
        when(valueOps.get("k")).thenReturn("v");
        assertThat(cache.get("k")).isEqualTo("v");
    }

    @Test
    void delete_removesFromRedis() {
        cache.delete("k");
        verify(redisTemplate).delete("k");
    }

    @Test
    void set_fallsBackToInMemory_whenRedisThrows() {
        // Redis SET fails → value must still be retrievable from the in-memory cache
        doThrow(new RuntimeException("connection refused"))
                .when(valueOps).set(anyString(), any(), any());
        when(valueOps.get("k")).thenReturn(null); // Redis GET also returns nothing

        cache.set("k", "v", Duration.ofMinutes(5));

        assertThat(cache.get("k")).isEqualTo("v"); // served from in-memory fallback
    }

    @Test
    void get_returnsNull_whenRedisThrowsAndKeyNotInMemory() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(cache.get("missing")).isNull();
    }
}
