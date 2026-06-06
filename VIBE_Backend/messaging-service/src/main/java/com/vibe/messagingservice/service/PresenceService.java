package com.vibe.messagingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tracks user online presence using Redis.
 *
 * <h3>Architecture</h3>
 * Redis keys:
 * <ul>
 *   <li>{@code vibe:presence:{userId}} → "online" with TTL 65 seconds (heartbeat every 30s)</li>
 *   <li>{@code vibe:lastseen:{userId}} → ISO timestamp, no TTL</li>
 * </ul>
 *
 * <p>Frontend sends a heartbeat every 30 seconds via STOMP /app/presence.heartbeat.
 * If the key expires (no heartbeat), the user is considered offline.
 *
 * <h3>Push notifications</h3>
 * Presence changes are broadcast to /topic/presence so conversation lists
 * can show "Online" / "Last seen X" in real time — exactly like WhatsApp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_PREFIX  = "vibe:presence:";
    private static final String LASTSEEN_PREFIX  = "vibe:lastseen:";
    private static final long   PRESENCE_TTL_SEC = 65;

    private final StringRedisTemplate   redis;
    private final SimpMessagingTemplate ws;

    /**
     * Mark a user as online and broadcast the change.
     */
    public void userConnected(String userId) {
        boolean wasOffline = !isOnline(userId);

        redis.opsForValue().set(PRESENCE_PREFIX + userId, "online",
                PRESENCE_TTL_SEC, TimeUnit.SECONDS);

        if (wasOffline) {
            broadcastPresence(userId, "online", null);
            log.debug("[PRESENCE] {} is now ONLINE", userId);
        }
    }

    /**
     * Mark a user as offline and record last-seen time.
     */
    public void userDisconnected(String userId) {
        Instant now = Instant.now();
        redis.delete(PRESENCE_PREFIX + userId);
        redis.opsForValue().set(LASTSEEN_PREFIX + userId, now.toString());
        broadcastPresence(userId, "offline", now.toString());
        log.debug("[PRESENCE] {} is now OFFLINE (last seen: {})", userId, now);
    }

    /**
     * Refresh the heartbeat TTL (called on STOMP heartbeat from client).
     */
    public void heartbeat(String userId) {
        redis.expire(PRESENCE_PREFIX + userId, PRESENCE_TTL_SEC, TimeUnit.SECONDS);
    }

    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(redis.hasKey(PRESENCE_PREFIX + userId));
    }

    public String getLastSeen(String userId) {
        return redis.opsForValue().get(LASTSEEN_PREFIX + userId);
    }

    /**
     * Returns either "online" or the last-seen ISO timestamp.
     * This is what the frontend displays: "Online" or "Last seen Xm ago".
     */
    public Map<String, String> getPresenceInfo(String userId) {
        if (isOnline(userId)) {
            return Map.of("status", "online");
        }
        String lastSeen = getLastSeen(userId);
        return lastSeen != null
                ? Map.of("status", "offline", "lastSeen", lastSeen)
                : Map.of("status", "offline");
    }

    private void broadcastPresence(String userId, String status, String lastSeen) {
        try {
            Map<String, String> payload = lastSeen != null
                    ? Map.of("userId", userId, "status", status, "lastSeen", lastSeen)
                    : Map.of("userId", userId, "status", status);
            ws.convertAndSend("/topic/presence", payload);
        } catch (Exception e) {
            log.warn("[PRESENCE] Failed to broadcast presence for {}: {}", userId, e.getMessage());
        }
    }
}
