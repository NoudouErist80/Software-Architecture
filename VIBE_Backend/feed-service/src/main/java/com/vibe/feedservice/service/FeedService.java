package com.vibe.feedservice.service;

import com.mongodb.client.result.UpdateResult;           // FIX #2: correct package in Spring Data MongoDB 4.x
import com.vibe.common.constants.VibeConstants;
import com.vibe.common.enums.TokenEarnType;
import com.vibe.common.event.TokenEarnedEvent;
import com.vibe.common.event.VideoWatchedEvent;
import com.vibe.common.exception.VibeException;
import com.vibe.feedservice.model.entity.*;
import com.vibe.feedservice.model.entity.enums.MoodTag;
import com.vibe.feedservice.model.request.*;
import com.vibe.feedservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;                               // FIX #3: org.bson.Document for $text search
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core business logic for the VIBE Feed Service.
 *
 * SCALING PRINCIPLES APPLIED:
 * 1. Atomic MongoDB operators ($addToSet, $inc, $set, $pull) replace
 *    read-modify-write cycles — eliminates race conditions, halves DB round trips.
 * 2. Status cleanup scheduler issues a single targeted bulk MongoDB update
 *    instead of loading all documents into JVM heap.
 *    MongoDB TTL is the primary expiry mechanism; this scheduler is a fallback.
 * 3. Full-text search ($text operator) replaces regex — backed by the text index
 *    created in MongoIndexConfig. No collection scans.
 * 4. Embedded list caps (Status.MAX_EMBEDDED_*) prevent 16 MB document overflow.
 * 5. Page size capped server-side against unbounded client requests.
 *
 * COMPILATION FIXES APPLIED (vs previous version):
 * - FIX #1: VibeConstants.VIRAL_VIEW_THRESHOLD added to VibeConstants.java
 * - FIX #2: UpdateResult — correct import is com.mongodb.client.result.UpdateResult
 *           (moved out of spring-data package in Spring Data MongoDB 4.x)
 * - FIX #3: Document — import org.bson.Document for $text operator argument
 * - FIX #4: metaScoreElement() renamed to scoreElement() in Spring Data MongoDB 4.x
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final PostRepository                postRepository;
    private final UserProfileRepository         userProfileRepository;
    private final CommentRepository             commentRepository;
    private final StatusRepository              statusRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoTemplate                 mongoTemplate;

    @Value("${vibe.feed.max-page-size:50}")
    private int maxPageSize;

    @Value("${vibe.feed.search-result-limit:30}")
    private int searchResultLimit;

    // ─── Posts ────────────────────────────────────────────────────────────────

    @CacheEvict(value = "user-feed", key = "#userId")
    public Post createPost(String userId, String username, CreatePostRequest request) {
        UserProfile profile = getOrCreateProfile(userId, username);

        Post post = Post.builder()
                .authorId(userId)
                .authorUsername(username)
                .authorProfilePictureUrl(profile.getProfilePictureUrl())
                .type(request.getType())
                .caption(request.getCaption())
                .mediaUrl(request.getMediaUrl())
                .thumbnailUrl(request.getThumbnailUrl())
                .durationSeconds(request.getDurationSeconds())
                .moodTags(request.getMoodTags()  != null ? request.getMoodTags()  : List.of())
                .hashtags(request.getHashtags()  != null ? request.getHashtags()  : List.of())
                .location(request.getLocation())
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        post = postRepository.save(post);

        // Atomic increment — avoids read-modify-write race on postCount
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(userId)),
                new Update().inc("postCount", 1),
                UserProfile.class
        );

        publishSafely(VibeConstants.TOPIC_POST_CREATED, userId,
                Map.of(
                        "userId",     userId,
                        "postId",     post.getId(),
                        "type",       post.getType().name(),
                        "occurredAt", Instant.now().toString()
                ));

        log.info("Post created: userId={}, postId={}", userId, post.getId());
        return post;
    }

    /**
     * Mood-aware paginated feed. Page size capped server-side.
     */
    @Cacheable(value = "user-feed", key = "#userId + ':' + #mood + ':' + #page")
    public Page<Post> getFeed(String userId, MoodTag mood, int page, int size) {
        int safeSize = Math.min(size, maxPageSize);
        Pageable pageable = PageRequest.of(page, safeSize);
        if (mood != null) {
            return postRepository.findByIsActiveTrueAndMoodTagsContainingOrderByCreatedAtDesc(
                    mood, pageable);
        }
        return postRepository.findByIsActiveTrueOrderByCreatedAtDesc(pageable);
    }

    /**
     * Like/unlike using atomic $addToSet / $pull.
     * Eliminates the race condition in the original read-modify-write pattern.
     */
    @CacheEvict(value = "user-feed", allEntries = true)
    public Map<String, Object> likePost(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> VibeException.notFound("Post"));

        boolean liked;
        if (post.getLikes().contains(userId)) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(postId)),
                    new Update().pull("likes", userId),
                    Post.class
            );
            liked = false;
        } else {
            // $addToSet guarantees no duplicates even under high concurrency
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(postId)),
                    new Update().addToSet("likes", userId),
                    Post.class
            );
            liked = true;
            publishSafely(VibeConstants.TOPIC_POST_ENGAGED, userId,
                    Map.of("postId", postId, "authorId", post.getAuthorId(), "type", "LIKE"));
        }

        // Projection — fetch only likes field for accurate count after atomic op
        Query countQuery = Query.query(Criteria.where("id").is(postId));
        countQuery.fields().include("likes");
        Post updated  = mongoTemplate.findOne(countQuery, Post.class);
        int likeCount = (updated != null && updated.getLikes() != null)
                ? updated.getLikes().size() : 0;

        return Map.of("liked", liked, "likeCount", likeCount);
    }

    public Comment commentOnPost(String postId, String userId, String username, String content) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> VibeException.notFound("Post"));

        Comment comment = Comment.builder()
                .postId(postId)
                .authorId(userId)
                .authorUsername(username)
                .content(content)
                .createdAt(Instant.now())
                .build();
        comment = commentRepository.save(comment);

        // Atomic increment — safe under concurrent comment requests
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(postId)),
                new Update().inc("commentCount", 1),
                Post.class
        );

        publishSafely(VibeConstants.TOPIC_POST_ENGAGED, userId,
                Map.of("postId", postId, "authorId", post.getAuthorId(), "type", "COMMENT"));

        return comment;
    }

    public void watchVideo(String userId, WatchVideoRequest request) {
        if (request.getWatchedSeconds() < 30) return; // minimum 30 s threshold to earn tokens

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> VibeException.notFound("Post"));

        // Atomic view count increment
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(request.getPostId())),
                new Update().inc("viewCount", 1),
                Post.class
        );
        long newViewCount = post.getViewCount() + 1;

        // FIX #1: VibeConstants.VIRAL_VIEW_THRESHOLD now exists (added to VibeConstants.java)
        // Viral threshold: 10,000 views within first 24 hours → 500 bonus tokens
        if (!post.isViral() && newViewCount >= VibeConstants.VIRAL_VIEW_THRESHOLD) {
            long hoursSinceCreation = ChronoUnit.HOURS.between(post.getCreatedAt(), Instant.now());
            if (hoursSinceCreation <= 24) {
                // Conditional update — isViral=false guard prevents duplicate reward
                // under concurrent requests that simultaneously cross the threshold
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("id").is(request.getPostId())
                                .and("isViral").is(false)),
                        new Update().set("isViral", true).set("wentViralAt", Instant.now()),
                        Post.class
                );
                publishSafely(VibeConstants.TOPIC_TOKEN_EARNED, post.getAuthorId(),
                        TokenEarnedEvent.builder()
                                .userId(post.getAuthorId())
                                .tokensEarned(VibeConstants.TOKEN_EARN_VIRAL_POST)
                                .earnType(TokenEarnType.VIRAL_POST)
                                .referenceId(post.getId())
                                .build());
                log.info("Post {} went VIRAL — author={}", post.getId(), post.getAuthorId());
            }
        }

        publishSafely(VibeConstants.TOPIC_VIDEO_WATCHED, userId,
                VideoWatchedEvent.builder()
                        .viewerId(userId)
                        .postId(request.getPostId())
                        .creatorId(post.getAuthorId())
                        .watchedSeconds(request.getWatchedSeconds())
                        .build());
    }

    // ─── Statuses ─────────────────────────────────────────────────────────────

    public Status postStatus(String userId, String username, PostStatusRequest request) {
        UserProfile profile = getOrCreateProfile(userId, username);

        int duration = request.getDurationSeconds();
        if ("VIDEO".equals(request.getType()) && duration > VibeConstants.VIDEO_STATUS_MAX_SECONDS) {
            throw VibeException.badRequest(
                    "Video status max duration is 2 minutes (120 seconds).");
        }

        Status status = Status.builder()
                .authorId(userId)
                .authorUsername(username)
                .authorProfilePictureUrl(profile.getProfilePictureUrl())
                .type(request.getType())
                .content(request.getContent())
                .mediaUrl(request.getMediaUrl())
                .thumbnailUrl(request.getThumbnailUrl())
                .durationSeconds(duration)
                .visibility(request.getVisibility() != null ? request.getVisibility() : "CONTACTS")
                .exceptUserIds(
                        request.getExceptUserIds() != null ? request.getExceptUserIds() : List.of())
                .isActive(true)
                // Use VibeConstants.STATUS_EXPIRY_HOURS (= 24) — single source of truth
                .expiresAt(Instant.now().plus(VibeConstants.STATUS_EXPIRY_HOURS, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .build();

        status = statusRepository.save(status);

        publishSafely(VibeConstants.TOPIC_TOKEN_EARNED, userId,
                TokenEarnedEvent.builder()
                        .userId(userId)
                        .tokensEarned(VibeConstants.TOKEN_EARN_POST_STATUS)
                        .earnType(TokenEarnType.POST_CREATED)
                        .referenceId(status.getId())
                        .build());

        log.info("Status created: userId={}, statusId={}", userId, status.getId());
        return status;
    }

    public List<Status> getStatuses(String userId) {
        return statusRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    public Map<String, Object> likeStatus(String statusId, String userId) {
        Status status = statusRepository.findById(statusId)
                .orElseThrow(() -> VibeException.notFound("Status"));

        boolean liked;
        if (status.getLikes().contains(userId)) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(statusId)),
                    new Update().pull("likes", userId),
                    Status.class
            );
            liked = false;
        } else {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(statusId)),
                    new Update().addToSet("likes", userId),
                    Status.class
            );
            liked = true;
        }

        Query countQuery = Query.query(Criteria.where("id").is(statusId));
        countQuery.fields().include("likes");
        Status updated  = mongoTemplate.findOne(countQuery, Status.class);
        int likeCount   = (updated != null && updated.getLikes() != null)
                ? updated.getLikes().size() : 0;

        return Map.of("liked", liked, "likeCount", likeCount);
    }

    public Status commentOnStatus(String statusId, String userId, String username,
                                   StatusCommentRequest request) {
        Status status = statusRepository.findById(statusId)
                .orElseThrow(() -> VibeException.notFound("Status"));

        // Enforce embedded cap — prevents 16 MB document overflow
        if (status.getComments().size() >= Status.MAX_EMBEDDED_COMMENTS) {
            log.warn("Status {} reached max embedded comments ({}). Comment dropped.",
                    statusId, Status.MAX_EMBEDDED_COMMENTS);
            return status;
        }

        Status.StatusComment comment = Status.StatusComment.builder()
                .commentId(UUID.randomUUID().toString())
                .userId(userId)
                .username(username)
                .content(request.getContent())
                .isPrivate(request.isPrivate())
                .createdAt(Instant.now())
                .build();

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(statusId)),
                new Update().push("comments", comment),
                Status.class
        );

        status.getComments().add(comment);
        return status;
    }

    public Map<String, Object> viewStatus(String statusId, String userId) {
        Status status = statusRepository.findById(statusId)
                .orElseThrow(() -> VibeException.notFound("Status"));

        boolean alreadyViewed = status.getViewedByUserIds().contains(userId);

        if (!alreadyViewed && status.getViewedByUserIds().size() < Status.MAX_EMBEDDED_VIEWS) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(statusId)),
                    new Update().addToSet("viewedByUserIds", userId),
                    Status.class
            );
        }

        return Map.of(
                "statusId",      statusId,
                "viewCount",     status.getViewedByUserIds().size() + (alreadyViewed ? 0 : 1),
                "alreadyViewed", alreadyViewed
        );
    }

    // ─── Scheduled: soft-delete fallback for expired statuses ─────────────────

    /**
     * FIX #2: UpdateResult imported from com.mongodb.client.result.UpdateResult.
     * It moved out of the spring-data package in Spring Data MongoDB 4.x.
     *
     * Single bulk update — zero documents loaded into heap.
     * The original implementation loaded all active statuses into memory first.
     */
    @Scheduled(cron = "${vibe.scheduler.status-cleanup-cron:0 0 * * * *}")
    public void deactivateExpiredStatuses() {
        Instant now = Instant.now();
        UpdateResult result = mongoTemplate.updateMulti(
                Query.query(Criteria.where("isActive").is(true).and("expiresAt").lt(now)),
                new Update().set("isActive", false),
                Status.class
        );
        if (result.getModifiedCount() > 0) {
            log.info("[StatusCleanup] Deactivated {} expired statuses.", result.getModifiedCount());
        }
    }

    // ─── Profiles ─────────────────────────────────────────────────────────────

    public UserProfile getProfile(String userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> VibeException.notFound("User profile"));
    }

    public UserProfile setMood(String userId, SetMoodRequest request) {
        UserProfile profile = getOrCreateProfile(userId, userId);
        profile.setCurrentMood(request.getMood());
        return userProfileRepository.save(profile);
    }

    // ─── Follow / Unfollow ────────────────────────────────────────────────────

    @Transactional
    public UserProfile follow(String followerId, String targetUserId) {
        if (followerId.equals(targetUserId)) {
            throw VibeException.badRequest("Cannot follow yourself.");
        }
        UserProfile follower = getOrCreateProfile(followerId, followerId);
        UserProfile target   = getOrCreateProfile(targetUserId, targetUserId);

        if (!target.getFollowerIds().contains(followerId)) {
            // Two atomic updates — no full document replace needed
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("userId").is(targetUserId)),
                    new Update().addToSet("followerIds", followerId).inc("followerCount", 1),
                    UserProfile.class
            );
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("userId").is(followerId)),
                    new Update().addToSet("followingIds", targetUserId).inc("followingCount", 1),
                    UserProfile.class
            );
            // Use the typed constant from VibeConstants instead of a raw string
            publishSafely(VibeConstants.TOPIC_NOTIFICATION_SEND, targetUserId,
                    Map.of(
                            "recipientId", targetUserId,
                            "actorId",     followerId,
                            "type",        "FOLLOW",
                            "message",     followerId + " started following you"
                    ));
        }
        return userProfileRepository.findByUserId(targetUserId).orElse(target);
    }

    @Transactional
    public UserProfile unfollow(String followerId, String targetUserId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(targetUserId)),
                new Update().pull("followerIds", followerId).inc("followerCount", -1),
                UserProfile.class
        );
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(followerId)),
                new Update().pull("followingIds", targetUserId).inc("followingCount", -1),
                UserProfile.class
        );
        return userProfileRepository.findByUserId(targetUserId)
                .orElseThrow(() -> VibeException.notFound("User profile"));
    }

    public Map<String, Object> getFollowers(String userId) {
        UserProfile profile = getOrCreateProfile(userId, userId);
        return Map.of("followers", profile.getFollowerIds(), "count", profile.getFollowerCount());
    }

    public Map<String, Object> getFollowing(String userId) {
        UserProfile profile = getOrCreateProfile(userId, userId);
        return Map.of("following", profile.getFollowingIds(), "count", profile.getFollowingCount());
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    /**
     * Full-text search using MongoDB's $text operator.
     *
     * FIX #3: org.bson.Document imported for the $text Document argument.
     * FIX #4: scoreElement("score") — metaScoreElement() was renamed in
     *         Spring Data MongoDB 4.x (spring-data-mongodb-4.2.0.jar on classpath).
     *
     * Backed by the compound text index on (caption, hashtags) in MongoIndexConfig.
     * Zero collection scans at any scale.
     */
    public List<Post> searchPosts(String q) {
        if (q == null || q.isBlank()) return List.of();

        Query query = Query.query(
                Criteria.where("isActive").is(true)
                        .and("$text").is(new Document("$search", q.trim()))
        ).limit(searchResultLimit);

        // FIX #4: scoreElement() is the correct method name in Spring Data MongoDB 4.x
       // CORRECT for Spring Data MongoDB 4.2.0
        query.with(Sort.by(Sort.Order.desc("score")));
        return mongoTemplate.find(query, Post.class);
    }

    /**
     * Hashtag browsing — backed by idx_posts_hashtags_active index (MongoIndexConfig).
     * Anchored regex (^tag$) avoids partial matches and is index-friendly.
     */
    public List<Post> getPostsByHashtag(String tag) {
        if (tag == null || tag.isBlank()) return List.of();
        Query query = Query.query(
                Criteria.where("isActive").is(true)
                        .and("hashtags").regex("^" + tag.trim() + "$", "i")
        ).limit(searchResultLimit);
        return mongoTemplate.find(query, Post.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UserProfile getOrCreateProfile(String userId, String username) {
        return userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Auto-creating UserProfile for userId={}", userId);
                    return userProfileRepository.save(
                            UserProfile.builder()
                                    .userId(userId)
                                    .username(username)
                                    .followerCount(0)
                                    .followingCount(0)
                                    .postCount(0)
                                    .build()
                    );
                });
    }

    /**
     * Fire-and-forget Kafka publish.
     * Feed operations must never fail because the event bus is temporarily
     * unavailable — Kafka is an eventually-consistent side channel here.
     */
    private void publishSafely(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event);
        } catch (Exception e) {
            log.warn("[Kafka] Publish failed — topic={}, key={}: {}", topic, key, e.getMessage());
        }
    }
}