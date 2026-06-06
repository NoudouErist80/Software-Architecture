package com.vibe.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis-optional token cache.
 *
 * Attempts to use Redis for all token/OTP storage. If Redis is unavailable
 * (connection refused, timeout, etc.) the service transparently falls back to
 * a thread-safe in-memory ConcurrentHashMap so that registration and login
 * continue to work in local-dev environments where Redis is not running.
 *
 * The in-memory fallback is NOT cluster-safe — it is intentionally scoped to
 * the single JVM process. For production deployments Redis must be running.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    // In-memory fallback — used when Redis is not reachable
    private final Map<String, String> localCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "token-cache-evict");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean redisAvailable = true;  // optimistic; re-evaluated per call

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Store a value with a TTL. Falls back to in-memory on Redis failure. */
    public void set(String key, String value, Duration ttl) {
        if (tryRedisSet(key, value, ttl)) return;
        setLocal(key, value, ttl);
    }

    /** Retrieve a value. Falls back to in-memory on Redis failure. */
    public String get(String key) {
        String fromRedis = tryRedisGet(key);
        if (fromRedis != null) return fromRedis;
        return localCache.get(key);
    }

    /** Delete a key. Best-effort on both stores. */
    public void delete(String key) {
        tryRedisDelete(key);
        localCache.remove(key);
    }

    // ─── Redis helpers with graceful degradation ──────────────────────────────

    private boolean tryRedisSet(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            if (!redisAvailable) {
                log.info("✅ Redis connection restored.");
                redisAvailable = true;
            }
            return true;
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("⚠️  Redis unavailable — falling back to in-memory cache. " +
                         "Start Redis for production reliability. Error: {}", e.getMessage());
                redisAvailable = false;
            }
            return false;
        }
    }

    private String tryRedisGet(String key) {
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (!redisAvailable && val != null) {
                log.info("✅ Redis connection restored.");
                redisAvailable = true;
            }
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            if (redisAvailable) {
                log.warn("⚠️  Redis unavailable on GET — using in-memory fallback. Error: {}", e.getMessage());
                redisAvailable = false;
            }
            return null;
        }
    }

    private void tryRedisDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Redis delete failed for key '{}': {}", key, e.getMessage());
        }
    }

    // ─── In-memory fallback helpers ───────────────────────────────────────────

    private void setLocal(String key, String value, Duration ttl) {
        localCache.put(key, value);
        // Schedule eviction to avoid unbounded growth
        evictionScheduler.schedule(
                () -> localCache.remove(key),
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
        log.debug("📦 In-memory cache SET: key='{}', ttl={}s", key, ttl.toSeconds());
    }
}