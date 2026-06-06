package com.vibe.messagingservice.controller;

import com.vibe.common.response.ApiResponse;
import com.vibe.messagingservice.model.entity.Conversation.AdminPrivileges;
import com.vibe.messagingservice.model.request.*;
import com.vibe.messagingservice.service.AiSummaryService;
import com.vibe.messagingservice.service.MessagingService;
import com.vibe.messagingservice.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST + WebSocket controller for VIBE messaging.
 *
 * <h3>REST endpoints</h3>
 * All REST methods receive userId/username from the gateway-injected
 * X-User-Id and X-Username headers (the gateway validated the JWT).
 *
 * <h3>WebSocket @MessageMapping methods</h3>
 * Principal is resolved by {@link com.vibe.messagingservice.config.WebSocketAuthInterceptor}
 * from the STOMP CONNECT frame JWT. Always guard against null principal.
 *
 * <h3>Message delivery flow</h3>
 * REST POST /conversations/{id}/messages → MessagingService.sendMessage()
 *   → saves to MongoDB
 *   → pushes to /topic/conversation/{id}           (all topic subscribers)
 *   → pushes to /user/{recipientId}/queue/messages  (personal queue per recipient)
 *
 * The dual push ensures recipients receive messages whether or not they have
 * the conversation open.  The frontend subscribes to /user/queue/messages on
 * connect to handle messages for conversations that are not currently open.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/messaging")
@RequiredArgsConstructor
@Tag(name = "Messaging", description = "VIBE real-time messaging — chats, groups, communities, AI")
public class MessageController {

    private final MessagingService      messagingService;
    private final AiSummaryService      aiSummaryService;
    private final SimpMessagingTemplate simpMessaging;
    private final PresenceService       presenceService;

    // ─── WebSocket message handlers ──────────────────────────────────────────

    /**
     * Typing indicator — broadcast to all conversation subscribers.
     * FIXED: Guards against null principal (unauthenticated WS connections).
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) {
            log.warn("[WS] handleTyping called with null principal — ignoring");
            return;
        }
        String userId  = principal.getName(); // userId from JWT
        String convoId = (String) payload.get("conversationId");
        boolean typing = Boolean.TRUE.equals(payload.get("isTyping"));
        if (convoId == null) return;

        simpMessaging.convertAndSend(
                "/topic/conversation/" + convoId + "/typing",
                Map.of("userId", userId, "isTyping", typing));
    }

    /**
     * WebSocket message send — alternative to REST POST.
     * NOTE: Prefer the REST path (POST /conversations/{id}/messages).
     * This WS path is kept for backwards compatibility / low-latency use.
     * The REST path is the primary send path because it returns the saved
     * message ID to the sender immediately.
     */
    @MessageMapping("/chat.send")
    public void handleSendWebSocket(@Payload SendMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("[WS] chat.send called with null principal — ignoring");
            return;
        }
        // Extract username from the auth details (set in WebSocketAuthInterceptor)
        String username = principal.getName();
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth
                && auth.getDetails() instanceof String detailUsername) {
            username = detailUsername;
        }
        messagingService.sendMessage(principal.getName(), username, null, request);
    }

    /**
     * Client ACKs receipt of messages (triggers SENT → DELIVERED).
     */
    @MessageMapping("/chat.delivered")
    public void handleDelivered(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;
        String convoId    = (String) payload.get("conversationId");
        @SuppressWarnings("unchecked")
        List<String> ids  = (List<String>) payload.get("messageIds");
        if (convoId == null || ids == null || ids.isEmpty()) return;
        messagingService.markDelivered(convoId, principal.getName(), ids);
    }

    /**
     * Presence heartbeat — refreshes online TTL in Redis.
     */
    @MessageMapping("/presence.heartbeat")
    public void handleHeartbeat(Principal principal) {
        if (principal == null) return;
        presenceService.heartbeat(principal.getName());
    }

    /**
     * Presence connect/disconnect broadcast.
     */
    @MessageMapping("/presence")
    public void handlePresence(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;
        String status = (String) payload.getOrDefault("status", "online");
        if ("online".equals(status)) {
            presenceService.userConnected(principal.getName());
        } else {
            presenceService.userDisconnected(principal.getName());
        }
    }

    // ─── Conversations ────────────────────────────────────────────────────────

    @GetMapping("/conversations")
    @Operation(summary = "Get all conversations for the current user")
    public ResponseEntity<ApiResponse<Object>> getConversations(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(messagingService.getConversations(userId)));
    }

    /**
     * Create or retrieve a 1-to-1 conversation.
     *
     * The request body may include:
     *   - participantId       (required) — UUID of the other user
     *   - participantUsername (optional) — their @username
     *   - participantPhone    (optional) — their E.164 phone number
     *
     * participantPhone enables WhatsApp-style "unknown contact" display:
     * if the recipient hasn't saved the caller as a contact, they see the
     * phone number (e.g. "+237 677 123 456") as the conversation name.
     *
     * The caller's own phone number is NOT available in this service
     * (the gateway only forwards X-User-Id, X-Username, X-User-Role).
     * If the caller's phone is needed, it should be fetched from auth-service
     * or stored in JWT claims.  For now we pass null for the caller's phone
     * and the conversation stores an empty string in slot[0].
     */
    @PostMapping("/conversations/direct")
    @Operation(summary = "Create or retrieve a 1-to-1 conversation")
    public ResponseEntity<ApiResponse<Object>> createDirect(
            @Valid @RequestBody CreateDirectRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messagingService.createDirect(userId, username, null, request)));
    }

    @PostMapping("/conversations/group")
    @Operation(summary = "Create a new group conversation")
    public ResponseEntity<ApiResponse<Object>> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messagingService.createGroup(userId, username, request)));
    }

    @PostMapping("/conversations/community")
    @Operation(summary = "Create a new community with auto-generated Announcements channel")
    public ResponseEntity<ApiResponse<Object>> createCommunity(
            @Valid @RequestBody CreateGroupRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(messagingService.createCommunity(userId, username, request)));
    }

    @PatchMapping("/conversations/{conversationId}/read")
    @Operation(summary = "Mark conversation as read — triggers blue ticks for sender")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable("conversationId") String conversationId,
            @RequestHeader("X-User-Id") String userId) {
        messagingService.markRead(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PatchMapping("/conversations/{conversationId}/delivered")
    @Operation(summary = "ACK message delivery (SENT → DELIVERED)")
    public ResponseEntity<ApiResponse<Void>> markDelivered(
            @PathVariable("conversationId") String conversationId,
            @RequestBody Map<String, List<String>> body,
            @RequestHeader("X-User-Id") String userId) {
        List<String> messageIds = body.get("messageIds");
        if (messageIds != null && !messageIds.isEmpty()) {
            messagingService.markDelivered(conversationId, userId, messageIds);
        }
        return ResponseEntity.ok(ApiResponse.success("Delivery ACKed", null));
    }

    // ─── Messages ─────────────────────────────────────────────────────────────

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Get paginated message history (also delivers offline messages)")
    public ResponseEntity<ApiResponse<Object>> getMessages(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                messagingService.getMessages(conversationId, userId, page, size)));
    }

    /**
     * Send a message via REST (primary path).
     *
     * This is the canonical message-send path.  The MessagingService:
     *   1. Persists the message to MongoDB
     *   2. Broadcasts to /topic/conversation/{id}  (open chat area)
     *   3. Pushes to /user/{recipientId}/queue/messages  (conversation list)
     *
     * The frontend must NOT call wsService.sendMessage() after this REST call —
     * doing so would result in the backend processing and broadcasting the
     * message a second time, causing duplicates for all recipients.
     */
    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a message (primary REST path — supports offline delivery)")
    public ResponseEntity<ApiResponse<Object>> sendMessage(
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        request.setConversationId(conversationId);
        return ResponseEntity.ok(ApiResponse.success("Message sent",
                messagingService.sendMessage(userId, username, null, request)));
    }

    @PatchMapping("/messages/{messageId}")
    @Operation(summary = "Edit a message (within 15-minute window)")
    public ResponseEntity<ApiResponse<Object>> editMessage(
            @PathVariable("messageId") String messageId,
            @Valid @RequestBody EditMessageRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success("Message edited",
                messagingService.editMessage(messageId, userId, request.getContent())));
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete a message (admin override ignores 15-min rule)")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable("messageId") String messageId,
            @RequestParam(name = "forEveryone", defaultValue = "false") boolean forEveryone,
            @RequestHeader("X-User-Id") String userId) {
        messagingService.deleteMessage(messageId, userId, forEveryone);
        return ResponseEntity.ok(ApiResponse.success("Message deleted", null));
    }

    @PostMapping("/messages/{messageId}/translate")
    @Operation(summary = "Translate a message to target language (11 African + global languages)")
    public CompletableFuture<ResponseEntity<ApiResponse<Object>>> translateMessage(
            @PathVariable("messageId") String messageId,
            @Valid @RequestBody TranslateMessageRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return aiSummaryService.translateStoredMessage(messageId, request.getTargetLanguage())
                .thenApply(msg -> ResponseEntity.ok(ApiResponse.success("Translated", msg)));
    }

    // ─── Admin management ─────────────────────────────────────────────────────

    @PostMapping("/conversations/{conversationId}/admins/{targetUserId}/super")
    @Operation(summary = "Promote a member to super-admin (VIBE unique: shared ownership)")
    public ResponseEntity<ApiResponse<Object>> promoteSuperAdmin(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("targetUserId") String targetUserId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success("Promoted to super-admin",
                messagingService.promoteSuperAdmin(conversationId, userId, targetUserId)));
    }

    @PostMapping("/conversations/{conversationId}/admins/{targetUserId}")
    @Operation(summary = "Appoint an admin with specific privileges")
    public ResponseEntity<ApiResponse<Object>> appointAdmin(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("targetUserId") String targetUserId,
            @RequestBody AdminPrivileges privileges,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success("Admin appointed",
                messagingService.appointAdmin(conversationId, userId, targetUserId, privileges)));
    }

    @DeleteMapping("/conversations/{conversationId}/admins/{targetUserId}")
    @Operation(summary = "Remove admin/super-admin status (creator is immutable)")
    public ResponseEntity<ApiResponse<Object>> removeAdmin(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("targetUserId") String targetUserId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success("Admin removed",
                messagingService.removeAdmin(conversationId, userId, targetUserId)));
    }

    @DeleteMapping("/conversations/{conversationId}/members/{targetUserId}")
    @Operation(summary = "Remove a member from a group")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("targetUserId") String targetUserId,
            @RequestHeader("X-User-Id") String userId) {
        messagingService.removeMember(conversationId, userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }

    // ─── Presence ─────────────────────────────────────────────────────────────

    @GetMapping("/presence/{userId}")
    @Operation(summary = "Get user presence (online status or last seen)")
    public ResponseEntity<ApiResponse<Object>> getUserPresence(
            @PathVariable("userId") String userId,
            @RequestHeader("X-User-Id") String requesterId) {
        return ResponseEntity.ok(ApiResponse.success(messagingService.getUserPresence(userId)));
    }

    // ─── AI Features ──────────────────────────────────────────────────────────

    @GetMapping("/conversations/{conversationId}/ai-summary/unread")
    @Operation(summary = "AI 'Catch me up' — summary of unread messages in chosen language")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> summariseUnread(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(name = "language", defaultValue = "en") String language,
            @RequestHeader("X-User-Id") String userId) {
        return aiSummaryService.summariseUnread(conversationId, userId, language)
                .thenApply(s -> ResponseEntity.ok(ApiResponse.success("Summary ready", s)));
    }

    @GetMapping("/conversations/{conversationId}/ai-summary/recall")
    @Operation(summary = "AI Recall — summary of past messages by date range")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> summariseDateRange(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(name = "fromDate") String fromDate,
            @RequestParam(name = "toDate") String toDate,
            @RequestParam(name = "language", defaultValue = "en") String language,
            @RequestHeader("X-User-Id") String userId) {
        return aiSummaryService.summariseDateRange(conversationId, userId, language, fromDate, toDate)
                .thenApply(s -> ResponseEntity.ok(ApiResponse.success("Recall summary ready", s)));
    }

    @PostMapping("/conversations/{conversationId}/media")
    @Operation(summary = "Get pre-signed URL for media upload to a conversation")
    public ResponseEntity<ApiResponse<Object>> uploadMedia(
            @PathVariable("conversationId") String conversationId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("uploadUrl", "/api/v1/media/upload?context=message&conversationId=" + conversationId)));
    }
}