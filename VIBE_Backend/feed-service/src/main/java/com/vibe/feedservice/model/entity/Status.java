package com.vibe.feedservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;

/**
 * VIBE Status — ephemeral content visible for 24 hours (like WhatsApp Stories
 * but with up to 2-minute video and richer social reactions).
 *
 * SCALING NOTES:
 * - TTL expiry is handled by MongoDB natively via the index in MongoIndexConfig.
 *   The @Indexed annotation on expiresAt has been intentionally removed to
 *   prevent Spring from attempting auto-index creation with conflicting options.
 * - viewedByUserIds and comments are capped at 500 entries embedded in the
 *   document to prevent hitting MongoDB's 16 MB document size limit.
 *   At billions of users, high-engagement statuses should migrate views/comments
 *   to a dedicated collection (StatusView, StatusComment collections).
 * - The isActive soft-delete flag is retained as a fallback in case the MongoDB
 *   TTL reaper hasn't yet processed a document.
 */
@Document(collection = "statuses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Status {

    @Id
    private String id;

    /** Indexed in MongoIndexConfig — do NOT add @Indexed here. */
    private String authorId;
    private String authorUsername;
    private String authorProfilePictureUrl;

    /** TEXT | IMAGE | VIDEO */
    private String type;
    /** Text body for TEXT-type statuses */
    private String content;
    private String mediaUrl;
    private String thumbnailUrl;
    /** Validated ≤ 120 seconds for VIDEO type in FeedService */
    private int durationSeconds;

    /** EVERYONE | CONTACTS | FOLLOWERS | EXCEPT */
    private String visibility;

    /** Excluded user IDs when visibility = EXCEPT */
    @Builder.Default
    private List<String> exceptUserIds = new ArrayList<>();

    /** User IDs who liked this status (Set guarantees uniqueness) */
    @Builder.Default
    private Set<String> likes = new HashSet<>();

    /**
     * Embedded public comments — capped at MAX_EMBEDDED_COMMENTS.
     * Once the cap is reached, further comments are silently dropped at the
     * document level; the service layer enforces the cap before saving.
     * For production at scale, migrate to a dedicated StatusComment collection.
     */
    @Builder.Default
    private List<StatusComment> comments = new ArrayList<>();

    /**
     * Who viewed this status — capped at MAX_EMBEDDED_VIEWS.
     * Exact view counts beyond the cap are tracked via a Redis counter keyed
     * "status:views:{statusId}" and reconciled asynchronously.
     */
    @Builder.Default
    private List<String> viewedByUserIds = new ArrayList<>();

    /** Soft-delete flag — TTL is the primary expiry mechanism */
    private boolean isActive;

    /**
     * The instant this status expires (createdAt + 24h).
     * The TTL index on this field (managed by MongoIndexConfig) instructs
     * MongoDB to automatically delete the document after this time.
     * NOTE: @Indexed is intentionally absent — MongoIndexConfig owns all indexes.
     */
    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    // ─── Embedded cap constants ────────────────────────────────────────────────

    /** Maximum comments stored inline in the status document. */
    public static final int MAX_EMBEDDED_COMMENTS = 500;

    /** Maximum viewer IDs stored inline in the status document. */
    public static final int MAX_EMBEDDED_VIEWS = 500;

    // ─── Embedded types ───────────────────────────────────────────────────────

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusComment {
        private String commentId;
        private String userId;
        private String username;
        private String content;
        /** true = reply goes to DM rather than public comment thread */
        private boolean isPrivate;
        private Instant createdAt;
    }
}