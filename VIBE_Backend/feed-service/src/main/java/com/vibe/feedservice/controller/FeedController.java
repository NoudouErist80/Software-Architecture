package com.vibe.feedservice.controller;

import com.vibe.common.response.ApiResponse;
import com.vibe.feedservice.model.entity.enums.MoodTag;
import com.vibe.feedservice.model.request.*;
import com.vibe.feedservice.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "VIBE content feed — posts, statuses, comments, reactions")
public class FeedController {

    private final FeedService feedService;

    // ─── Posts ────────────────────────────────────────────────────────────────

    @PostMapping("/posts")
    @Operation(summary = "Create a new post (text, image, video, reel)")
    public ResponseEntity<ApiResponse<Object>> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post published!", feedService.createPost(userId, username, request)));
    }

    @GetMapping("/posts")
    @Operation(summary = "Get mood-aware feed (optional ?mood=RELAXED)")
    public ResponseEntity<ApiResponse<Object>> getFeed(
            @RequestParam(required = false) MoodTag mood,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.getFeed(userId, mood, page, size)));
    }

    @PostMapping("/posts/{postId}/like")
    @Operation(summary = "Like or unlike a post")
    public ResponseEntity<ApiResponse<Object>> likePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.likePost(postId, userId)));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Comment on a post")
    public ResponseEntity<ApiResponse<Object>> commentPost(
            @PathVariable String postId,
            @Valid @RequestBody CommentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(feedService.commentOnPost(postId, userId, username, request.getContent())));
    }

    @PostMapping("/posts/{postId}/watch")
    @Operation(summary = "Record video watch event (earns +2 tokens at 30s+)")
    public ResponseEntity<ApiResponse<Void>> watchVideo(
            @PathVariable String postId,
            @Valid @RequestBody WatchVideoRequest request,
            @RequestHeader("X-User-Id") String userId) {
        request.setPostId(postId);
        feedService.watchVideo(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Watch recorded!", null));
    }

    // ─── Statuses ─────────────────────────────────────────────────────────────

    @GetMapping("/statuses")
    @Operation(summary = "Get all active statuses visible to current user")
    public ResponseEntity<ApiResponse<Object>> getStatuses(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.getStatuses(userId)));
    }

    @PostMapping("/statuses")
    @Operation(summary = "Post a new status (text/image/video, max 2 min, visible 24h)")
    public ResponseEntity<ApiResponse<Object>> postStatus(
            @Valid @RequestBody PostStatusRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Status posted!", feedService.postStatus(userId, username, request)));
    }

    @PostMapping("/statuses/{statusId}/like")
    @Operation(summary = "Like or unlike a status")
    public ResponseEntity<ApiResponse<Object>> likeStatus(
            @PathVariable String statusId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.likeStatus(statusId, userId)));
    }

    @PostMapping("/statuses/{statusId}/comments")
    @Operation(summary = "Comment on a status (public or private DM reply)")
    public ResponseEntity<ApiResponse<Object>> commentStatus(
            @PathVariable String statusId,
            @Valid @RequestBody StatusCommentRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(feedService.commentOnStatus(statusId, userId, username, request)));
    }

    @PostMapping("/statuses/{statusId}/view")
    @Operation(summary = "Record a status view")
    public ResponseEntity<ApiResponse<Object>> viewStatus(
            @PathVariable String statusId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.viewStatus(statusId, userId)));
    }

    // ─── Profiles & Mood ──────────────────────────────────────────────────────

    @PutMapping("/mood")
    @Operation(summary = "Set current mood to personalise feed")
    public ResponseEntity<ApiResponse<Object>> setMood(
            @Valid @RequestBody SetMoodRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success("Mood updated! Your feed will match your vibe.",
                feedService.setMood(userId, request)));
    }

    @GetMapping("/profiles/{userId}")
    @Operation(summary = "Get a user's public profile")
    public ResponseEntity<ApiResponse<Object>> getProfile(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.getProfile(userId)));
    }

    // ─── Follow / Unfollow ────────────────────────────────────────────────────

    @PostMapping("/profiles/{targetId}/follow")
    @Operation(summary = "Follow a user")
    public ResponseEntity<ApiResponse<Object>> follow(
            @PathVariable String targetId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.follow(userId, targetId)));
    }

    @DeleteMapping("/profiles/{targetId}/follow")
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<ApiResponse<Object>> unfollow(
            @PathVariable String targetId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.unfollow(userId, targetId)));
    }

    @GetMapping("/profiles/{targetId}/followers")
    @Operation(summary = "Get followers of a user")
    public ResponseEntity<ApiResponse<Object>> getFollowers(@PathVariable String targetId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.getFollowers(targetId)));
    }

    @GetMapping("/profiles/{targetId}/following")
    @Operation(summary = "Get following list of a user")
    public ResponseEntity<ApiResponse<Object>> getFollowing(@PathVariable String targetId) {
        return ResponseEntity.ok(ApiResponse.success(feedService.getFollowing(targetId)));
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    @GetMapping("/posts/search")
    @Operation(summary = "Search posts by keyword in caption or hashtags")
    public ResponseEntity<ApiResponse<Object>> searchPosts(
            @RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(feedService.searchPosts(q)));
    }

    @GetMapping("/posts/hashtag/{tag}")
    @Operation(summary = "Get posts by hashtag")
    public ResponseEntity<ApiResponse<Object>> getPostsByHashtag(@PathVariable String tag) {
        return ResponseEntity.ok(ApiResponse.success(feedService.getPostsByHashtag(tag)));
    }
}
