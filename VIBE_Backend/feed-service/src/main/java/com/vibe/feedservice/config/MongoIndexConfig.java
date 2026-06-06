package com.vibe.feedservice.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.concurrent.TimeUnit;

/**
 * Explicit index management for VIBE Feed Service.
 *
 * WHY THIS EXISTS:
 * Spring's auto-index-creation causes startup failures when an index already
 * exists in MongoDB with different options (e.g. a TTL index with the wrong
 * expireAfterSeconds). By managing indexes here — after the application context
 * is fully ready — we can safely drop stale indexes before recreating them,
 * and we have full control over the process without risking collection locks
 * during bean initialization.
 *
 * This pattern is mandatory for any service expected to run at scale, where
 * index migrations must be deliberate and observable.
 *
 * ALL index changes for this service belong in this class.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    /**
     * Runs after the full ApplicationContext is initialized and the embedded
     * server is ready. Using ApplicationReadyEvent (not @PostConstruct) ensures
     * no beans are in a partially-initialized state when we talk to MongoDB.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        log.info("[MongoIndexConfig] Starting explicit index initialization...");
        try {
            ensureStatusIndexes();
            ensurePostIndexes();
            ensureUserProfileIndexes();
            ensureCommentIndexes();
            log.info("[MongoIndexConfig] All indexes verified successfully.");
        } catch (Exception e) {
            // Log but do not crash the application — the service can still run
            // without some indexes, degraded but alive. Ops will see this in logs.
            log.error("[MongoIndexConfig] Index initialization failed: {}", e.getMessage(), e);
        }
    }

    // ─── statuses collection ──────────────────────────────────────────────────

    private void ensureStatusIndexes() {
        final String collection = "statuses";
        log.debug("[MongoIndexConfig] Configuring indexes for '{}'", collection);

        // ── TTL index on expiresAt ──────────────────────────────────────────
        // This is the index that caused the startup crash.
        // Root cause: MongoDB had "expiresAt_1" with expireAfterSeconds=0
        // but the entity declared expireAfterSeconds=86400.
        // Fix: drop any conflicting index by key pattern, then recreate correctly.
        dropIndexByKeyIfExists(collection, "expiresAt");

        MongoCollection<Document> col = mongoTemplate.getCollection(collection);
        col.createIndex(
                Indexes.ascending("expiresAt"),
                new IndexOptions()
                        .name("idx_statuses_expiresAt_ttl")
                        .expireAfter(86400L, TimeUnit.SECONDS)   // 24 hours — matches entity
        );
        log.info("[MongoIndexConfig] TTL index on statuses.expiresAt created (86400s).");

        // ── authorId — used by getStatuses, follow feed queries ────────────
        IndexOperations ops = mongoTemplate.indexOps(collection);
        ops.ensureIndex(new Index().on("authorId", Sort.Direction.ASC)
                .named("idx_statuses_authorId"));

        // ── isActive + createdAt — primary feed query ──────────────────────
        ops.ensureIndex(new Index()
                .on("isActive", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("idx_statuses_active_createdAt"));

        // ── authorId + isActive — per-user status lookup ───────────────────
        ops.ensureIndex(new Index()
                .on("authorId", Sort.Direction.ASC)
                .on("isActive", Sort.Direction.ASC)
                .named("idx_statuses_authorId_active"));
    }

    // ─── posts collection ─────────────────────────────────────────────────────

    private void ensurePostIndexes() {
        final String collection = "posts";
        log.debug("[MongoIndexConfig] Configuring indexes for '{}'", collection);
        IndexOperations ops = mongoTemplate.indexOps(collection);

        // Primary feed: active posts sorted by time
        ops.ensureIndex(new Index()
                .on("isActive", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("idx_posts_active_createdAt"));

        // Mood-filtered feed
        ops.ensureIndex(new Index()
                .on("isActive", Sort.Direction.ASC)
                .on("moodTags", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("idx_posts_active_moodTags_createdAt"));

        // Author profile page
        ops.ensureIndex(new Index()
                .on("authorId", Sort.Direction.ASC)
                .on("isActive", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("idx_posts_authorId_active_createdAt"));

        // Hashtag browsing
        ops.ensureIndex(new Index()
                .on("hashtags", Sort.Direction.ASC)
                .on("isActive", Sort.Direction.ASC)
                .named("idx_posts_hashtags_active"));

        // Viral detection scheduler query
        ops.ensureIndex(new Index()
                .on("isViral", Sort.Direction.ASC)
                .on("viewCount", Sort.Direction.DESC)
                .named("idx_posts_viral_viewCount"));

        // Full-text search index — replaces regex, essential at scale
        // This supports FeedService.searchPosts() without collection scans
        MongoCollection<Document> col = mongoTemplate.getCollection(collection);
        try {
            col.createIndex(
                    Indexes.compoundIndex(
                            Indexes.text("caption"),
                            Indexes.text("hashtags")
                    ),
                    new IndexOptions().name("idx_posts_text_search")
            );
        } catch (Exception e) {
            // Text index may already exist — not critical on restart
            log.warn("[MongoIndexConfig] Text index on posts may already exist: {}", e.getMessage());
        }

        log.info("[MongoIndexConfig] Post indexes configured.");
    }

    // ─── userprofiles collection ──────────────────────────────────────────────

    private void ensureUserProfileIndexes() {
        final String collection = "userprofiles";
        log.debug("[MongoIndexConfig] Configuring indexes for '{}'", collection);
        IndexOperations ops = mongoTemplate.indexOps(collection);

        ops.ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .unique()
                .named("idx_userprofiles_userId_unique"));

        ops.ensureIndex(new Index()
                .on("username", Sort.Direction.ASC)
                .unique()
                .named("idx_userprofiles_username_unique"));

        log.info("[MongoIndexConfig] UserProfile indexes configured.");
    }

    // ─── comments collection ──────────────────────────────────────────────────

    private void ensureCommentIndexes() {
        final String collection = "comments";
        log.debug("[MongoIndexConfig] Configuring indexes for '{}'", collection);
        IndexOperations ops = mongoTemplate.indexOps(collection);

        ops.ensureIndex(new Index()
                .on("postId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("idx_comments_postId_createdAt"));

        log.info("[MongoIndexConfig] Comment indexes configured.");
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Drops ANY index on the given collection whose key document contains the
     * specified field, regardless of index name or options.
     * This is the safe way to handle TTL index migration without knowing the
     * exact stale index name ahead of time.
     */
    private void dropIndexByKeyIfExists(String collectionName, String fieldName) {
        try {
            MongoCollection<Document> col = mongoTemplate.getCollection(collectionName);
            for (Document index : col.listIndexes()) {
                Document key = index.get("key", Document.class);
                if (key != null && key.containsKey(fieldName)) {
                    String name = index.getString("name");
                    if (!"_id_".equals(name)) {  // never drop the _id index
                        col.dropIndex(name);
                        log.info("[MongoIndexConfig] Dropped stale index '{}' from '{}' (field: {})",
                                name, collectionName, fieldName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MongoIndexConfig] Could not inspect/drop index on field '{}' in '{}': {}",
                    fieldName, collectionName, e.getMessage());
        }
    }
}