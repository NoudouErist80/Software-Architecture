package com.vibe.messagingservice.service;

import com.vibe.common.constants.VibeConstants;
import com.vibe.common.event.MessageSentEvent;
import com.vibe.common.exception.VibeException;
import com.vibe.messagingservice.model.entity.Conversation;
import com.vibe.messagingservice.model.entity.Conversation.AdminPrivileges;
import com.vibe.messagingservice.model.entity.Message;
import com.vibe.messagingservice.model.entity.enums.ConversationType;
import com.vibe.messagingservice.model.entity.enums.DeliveryStatus;
import com.vibe.messagingservice.model.entity.enums.MessageType;
import com.vibe.messagingservice.model.request.*;
import com.vibe.messagingservice.repository.ConversationRepository;
import com.vibe.messagingservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core messaging service for VIBE.
 *
 * <h3>Offline message delivery (WhatsApp/Telegram parity)</h3>
 * Messages are persisted immediately on send. When an offline recipient
 * reconnects, the frontend's useEffect fetches conversation history via
 * REST — ensuring zero message loss even across long offline periods.
 *
 * <h3>Real-time delivery — dual-path push</h3>
 * Every new message is pushed via TWO WebSocket paths simultaneously:
 *
 * <ol>
 *   <li><b>/topic/conversation/{id}</b> — broadcast to all SUBSCRIBED clients.
 *       Used by the open ChatArea component.</li>
 *   <li><b>/user/{recipientId}/queue/messages</b> — personal queue per recipient.
 *       Used by clients that are connected but haven't opened the conversation
 *       yet (e.g. conversation list is visible but chat is not open).
 *       This is the key path that was missing and caused "message not received".</li>
 * </ol>
 *
 * <h3>Read receipt lifecycle</h3>
 * <pre>
 *   Sender sends message → deliveryStatus = SENT (1 grey tick)
 *   Recipient ACKs via STOMP /app/chat.delivered → DELIVERED (2 grey ticks)
 *   Recipient opens conversation → STOMP /app/chat.read → READ (2 blue ticks)
 * </pre>
 *
 * <h3>Group message layout</h3>
 * All messages in group chats appear on the LEFT side (Telegram style).
 * The sender is distinguished by a coloured username label and avatar.
 *
 * <h3>WhatsApp-style contact display</h3>
 * participantPhones is stored on the Conversation so that if a recipient
 * hasn't saved the sender as a contact, the UI displays the phone number
 * (e.g. "+237 677 123 456") instead of a blank or "Chat".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingService {

    private final MessageRepository      messageRepository;
    private final ConversationRepository conversationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessagingTemplate  websocketTemplate;
    private final PresenceService        presenceService;

    // ─── Send message ─────────────────────────────────────────────────────────

    /**
     * Persist a message and push it in real-time via dual WebSocket paths.
     *
     * <p>Offline recipients: the message is already in MongoDB.
     * When they reconnect, the frontend calls getMessages() which returns
     * everything including messages sent while they were away.
     *
     * @param senderId       Auth-service user UUID
     * @param senderUsername @username for display
     * @param senderPhone    E.164 phone for unknown-contact display
     * @param request        Message payload
     */
    public Message sendMessage(String senderId, String senderUsername,
                               String senderPhone, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> VibeException.notFound("Conversation"));

        if (!conversation.getParticipantIds().contains(senderId)) {
            throw VibeException.forbidden("Not a participant of this conversation");
        }

        // Check announcement channel restriction
        if (conversation.isAnnouncementsChannel() && !conversation.isAdmin(senderId)) {
            throw VibeException.forbidden("Only admins can post in announcement channels");
        }

        Message message = Message.builder()
                .conversationId(request.getConversationId())
                .senderId(senderId)
                .senderUsername(senderUsername)
                .senderPhone(senderPhone)
                .type(request.getType() != null ? request.getType() : MessageType.TEXT)
                .content(request.getContent())
                .mediaUrl(request.getMediaUrl())
                .thumbnailUrl(request.getThumbnailUrl())
                .replyToMessageId(request.getReplyToMessageId())
                .deliveryStatus(DeliveryStatus.SENT)
                .isEdited(false)
                .isDeleted(false)
                .isForwarded(Boolean.TRUE.equals(request.getIsForwarded()))
                .createdAt(Instant.now())
                .build();

        message = messageRepository.save(message);

        // Update conversation preview
        String preview = buildPreview(message);
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessagePreview(preview);
        conversation.setLastMessageAt(Instant.now());

        // Increment unread for offline/other participants only
        Map<String, Integer> unread = conversation.getUnreadCounts();
        if (unread == null) unread = new HashMap<>();
        for (String pid : conversation.getParticipantIds()) {
            if (!pid.equals(senderId)) {
                unread.merge(pid, 1, Integer::sum);
            }
        }
        conversation.setUnreadCounts(unread);
        conversationRepository.save(conversation);

        // ── Dual-path WebSocket push ──────────────────────────────────────────
        //
        // PATH 1: Broadcast to /topic/conversation/{id}
        //   → Received by any client subscribed to this conversation topic.
        //   → The open ChatArea subscribes here via wsService.subscribeToConversation().
        //   → The ConversationList also subscribes here via wsService.subscribeToConversationList().
        //
        // PATH 2: Personal queue /user/{recipientId}/queue/messages
        //   → Received by EACH recipient regardless of which topics they subscribed to.
        //   → Spring STOMP resolves /user/{id}/queue/X to the user's active session(s).
        //   → This ensures delivery even if the client hasn't subscribed to the topic yet.
        //   → The frontend's App.jsx / useEffect subscribes to this on connect.
        //
        // Together these two paths mirror what WhatsApp Web and Telegram Web do:
        // the conversation-topic push is for the open chat, and the personal queue
        // is the "push notification" channel that wakes up the rest of the UI.
        //
        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("type", "NEW_MESSAGE");
        wsPayload.put("message", message);
        wsPayload.put("conversationId", request.getConversationId());

        // PATH 1 — conversation topic (all subscribers on this topic)
        websocketTemplate.convertAndSend(
                "/topic/conversation/" + request.getConversationId(), wsPayload);

        // PATH 2 — personal queue for each non-sender participant
        List<String> recipients = new ArrayList<>(conversation.getParticipantIds());
        recipients.remove(senderId);
        for (String recipientId : recipients) {
            try {
                // Spring resolves /user/{principalName}/queue/messages
                // The principal name is the userId set in WebSocketAuthInterceptor.
                websocketTemplate.convertAndSendToUser(
                        recipientId, "/queue/messages", wsPayload);
            } catch (Exception e) {
                log.debug("[MSG] Could not push personal queue to {}: {}", recipientId, e.getMessage());
            }
        }

        // Immediately mark as DELIVERED for online recipients
        List<String> onlineRecipients = recipients.stream()
                .filter(presenceService::isOnline)
                .toList();

        if (!onlineRecipients.isEmpty()) {
            message.setDeliveredTo(new ArrayList<>(onlineRecipients));
            boolean allDelivered = message.isDeliveredToAll(recipients);
            if (allDelivered) {
                message.setDeliveryStatus(DeliveryStatus.DELIVERED);
                message.setDeliveredAt(Instant.now());
            }
            messageRepository.save(message);

            // Notify sender of delivery via their personal receipts queue
            pushDeliveryReceipt(senderId, message.getId(),
                    request.getConversationId(), DeliveryStatus.DELIVERED);
        }

        // Kafka: fire-and-forget for token rewards
        publishKafkaEvent(senderId, request.getConversationId(), message.getId(), conversation.isGroup());

        log.debug("[MSG] {} → conversation={} (status={})", senderId,
                request.getConversationId(), message.getDeliveryStatus());

        return message;
    }

    // Overload for backward compat (no phone)
    public Message sendMessage(String senderId, String senderUsername, SendMessageRequest request) {
        return sendMessage(senderId, senderUsername, null, request);
    }

    // ─── Delivery ACK ─────────────────────────────────────────────────────────

    /**
     * Called when a recipient's client ACKs message receipt (comes online).
     * Transitions SENT → DELIVERED for that recipient.
     */
    public void markDelivered(String conversationId, String recipientId, List<String> messageIds) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> VibeException.notFound("Conversation"));
        List<String> otherParticipants = conv.getParticipantIds().stream()
                .filter(p -> !p.equals(recipientId)).toList();

        for (String msgId : messageIds) {
            messageRepository.findById(msgId).ifPresent(msg -> {
                if (msg.isDeleted()) return;
                if (!msg.getDeliveredTo().contains(recipientId)) {
                    msg.getDeliveredTo().add(recipientId);
                    if (msg.isDeliveredToAll(otherParticipants.isEmpty() ? List.of(recipientId) : otherParticipants)) {
                        msg.setDeliveryStatus(DeliveryStatus.DELIVERED);
                        msg.setDeliveredAt(Instant.now());
                    }
                    messageRepository.save(msg);
                    // Notify sender
                    pushDeliveryReceipt(msg.getSenderId(), msgId, conversationId, DeliveryStatus.DELIVERED);
                }
            });
        }
    }

    // ─── Mark as Read ─────────────────────────────────────────────────────────

    /**
     * Mark all messages in a conversation as read by the given user.
     * Transitions DELIVERED → READ and notifies the sender with blue ticks.
     */
    public void markRead(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> VibeException.notFound("Conversation"));

        // Clear unread count
        Map<String, Integer> unread = conversation.getUnreadCounts();
        if (unread != null) unread.put(userId, 0);
        conversationRepository.save(conversation);

        // Mark all unread messages as read
        List<Message> unreadMessages = messageRepository.findUnreadMessages(conversationId, userId);
        List<String> sendersToNotify = new ArrayList<>();

        for (Message msg : unreadMessages) {
            if (!msg.getReadBy().contains(userId)) {
                msg.getReadBy().add(userId);
                msg.setDeliveryStatus(DeliveryStatus.READ);
                if (msg.getReadAt() == null) msg.setReadAt(Instant.now());
                messageRepository.save(msg);
                if (!sendersToNotify.contains(msg.getSenderId())) {
                    sendersToNotify.add(msg.getSenderId());
                }
            }
        }

        // Push read receipt on the conversation topic (all participants see it)
        websocketTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/read",
                Map.of("readBy", userId, "conversationId", conversationId,
                        "timestamp", Instant.now().toString()));

        // Notify each affected sender individually (blue tick)
        for (String senderId : sendersToNotify) {
            pushDeliveryReceipt(senderId, null, conversationId, DeliveryStatus.READ);
        }
    }

    // ─── Edit message ─────────────────────────────────────────────────────────

    public Message editMessage(String messageId, String userId, String newContent) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> VibeException.notFound("Message"));

        if (!message.getSenderId().equals(userId)) {
            throw VibeException.forbidden("Cannot edit someone else's message");
        }
        if (message.isDeleted()) {
            throw VibeException.badRequest("Cannot edit a deleted message");
        }

        long minutesSince = ChronoUnit.MINUTES.between(message.getCreatedAt(), Instant.now());
        if (minutesSince > VibeConstants.MESSAGE_EDIT_WINDOW_MINUTES) {
            throw VibeException.badRequest("Edit window has expired (15 minutes)");
        }

        message.setContent(newContent);
        message.setEdited(true);
        message.setEditedAt(Instant.now());
        message = messageRepository.save(message);

        websocketTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversationId(),
                Map.of("type", "MESSAGE_EDITED", "message", message));
        return message;
    }

    // ─── Delete message ───────────────────────────────────────────────────────

    public void deleteMessage(String messageId, String userId, boolean forEveryone) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> VibeException.notFound("Message"));

        Conversation conv = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> VibeException.notFound("Conversation"));

        // Admins can delete any message in their group
        boolean isAdminDelete = conv.isGroup() && conv.isAdmin(userId)
                && conv.getAdminPrivileges(userId) != null
                && conv.getAdminPrivileges(userId).isDeleteMessages();

        if (!message.getSenderId().equals(userId) && !isAdminDelete) {
            throw VibeException.forbidden("Cannot delete someone else's message");
        }

        if (forEveryone || isAdminDelete) {
            long minutesSince = ChronoUnit.MINUTES.between(message.getCreatedAt(), Instant.now());
            if (!isAdminDelete && minutesSince > VibeConstants.MESSAGE_DELETE_WINDOW_MINUTES) {
                throw VibeException.badRequest("Delete for everyone window expired (15 minutes)");
            }
            message.setDeleted(true);
            message.setContent("This message was deleted");
            messageRepository.save(message);
            websocketTemplate.convertAndSend(
                    "/topic/conversation/" + message.getConversationId(),
                    Map.of("type", "MESSAGE_DELETED", "messageId", messageId,
                            "forEveryone", true, "deletedBy", userId));
        } else {
            List<String> hiddenBy = message.getHiddenByUserIds();
            if (hiddenBy == null) hiddenBy = new ArrayList<>();
            hiddenBy.add(userId);
            message.setHiddenByUserIds(hiddenBy);
            messageRepository.save(message);
        }
    }

    // ─── Get messages ─────────────────────────────────────────────────────────

    public Page<Message> getMessages(String conversationId, String userId, int page, int size) {
        validateParticipant(conversationId, userId);
        int safeSize = Math.min(size, 100);
        return messageRepository.findByConversationIdAndNotHiddenByOrderByCreatedAtDesc(
                conversationId, userId, PageRequest.of(page, safeSize));
    }

    // ─── Conversations ────────────────────────────────────────────────────────

    public List<Conversation> getConversations(String userId) {
        return conversationRepository
                .findByParticipantIdsContainingOrderByLastMessageAtDesc(userId);
    }

    /**
     * Create or return an existing direct 1-to-1 conversation.
     *
     * <p>Both participants' usernames AND phone numbers are stored on the
     * Conversation document so either side can display the other's identity
     * without a round-trip to auth-service — exactly as WhatsApp Web does.
     *
     * <p>If a participant's phone is unknown at creation time an empty string
     * is stored; the frontend can update it later via a separate call.
     */
    public Conversation createDirect(String userId, String username,
                                     String userPhone, CreateDirectRequest request) {
        Optional<Conversation> existing = conversationRepository
                .findDirectBetween(userId, request.getParticipantId());
        if (existing.isPresent()) return existing.get();

        List<String> usernames = new ArrayList<>();
        usernames.add(username != null ? username : "");
        if (request.getParticipantUsername() != null) {
            usernames.add(request.getParticipantUsername());
        } else {
            usernames.add("");
        }

        List<String> phones = new ArrayList<>();
        phones.add(userPhone != null ? userPhone : "");
        phones.add(request.getParticipantPhone() != null ? request.getParticipantPhone() : "");

        Conversation conversation = Conversation.builder()
                .type(ConversationType.DIRECT)
                .creatorId(userId)
                .participantIds(new ArrayList<>(List.of(userId, request.getParticipantId())))
                .participantUsernames(usernames)
                .participantPhones(phones)
                .isGroup(false)
                .isActive(true)
                .createdAt(Instant.now())
                .unreadCounts(new HashMap<>())
                .superAdmins(new HashMap<>())
                .admins(new HashMap<>())
                .build();

        return conversationRepository.save(conversation);
    }

    // Backward compat overload
    public Conversation createDirect(String userId, String username, CreateDirectRequest request) {
        return createDirect(userId, username, null, request);
    }

    public Conversation createGroup(String userId, String username, CreateGroupRequest request) {
        List<String> participants = new ArrayList<>(request.getParticipantIds());
        if (!participants.contains(userId)) participants.add(0, userId);

        Map<String, Instant> superAdmins = new HashMap<>();
        superAdmins.put(userId, Instant.now()); // Creator is super-admin

        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.getName())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .creatorId(userId)
                .participantIds(participants)
                .isGroup(true)
                .isActive(true)
                .createdAt(Instant.now())
                .unreadCounts(new HashMap<>())
                .superAdmins(superAdmins)
                .admins(new HashMap<>())
                .requiresApproval(Boolean.TRUE.equals(request.getRequiresApproval()))
                .maxMembers(request.getMaxMembers() > 0 ? request.getMaxMembers() : 500)
                .build();

        return conversationRepository.save(conversation);
    }

    // ─── Community management ─────────────────────────────────────────────────

    public Conversation createCommunity(String userId, String username,
                                        CreateGroupRequest request) {
        Map<String, Instant> superAdmins = new HashMap<>();
        superAdmins.put(userId, Instant.now());

        // Create the community container
        Conversation community = Conversation.builder()
                .type(ConversationType.COMMUNITY)
                .name(request.getName())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .creatorId(userId)
                .participantIds(new ArrayList<>(List.of(userId)))
                .isGroup(true)
                .isActive(true)
                .createdAt(Instant.now())
                .unreadCounts(new HashMap<>())
                .superAdmins(superAdmins)
                .admins(new HashMap<>())
                .maxMembers(5000)
                .build();

        community = conversationRepository.save(community);

        // Auto-create the Announcements channel
        Conversation announcements = Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Announcements")
                .description("Official community announcements")
                .communityId(community.getId())
                .creatorId(userId)
                .participantIds(new ArrayList<>(List.of(userId)))
                .isGroup(true)
                .isAnnouncementsChannel(true)
                .isActive(true)
                .createdAt(Instant.now())
                .unreadCounts(new HashMap<>())
                .superAdmins(superAdmins)
                .admins(new HashMap<>())
                .build();
        announcements = conversationRepository.save(announcements);

        // Link announcement channel to community
        community.getSubGroupIds().add(announcements.getId());
        conversationRepository.save(community);

        return community;
    }

    // ─── Admin management ─────────────────────────────────────────────────────

    /**
     * Promote a member to super-admin.
     * Only existing super-admins can do this.
     */
    public Conversation promoteSuperAdmin(String conversationId, String actorId,
                                          String targetUserId) {
        Conversation conv = getAndValidate(conversationId, actorId);
        if (!conv.isSuperAdmin(actorId)) {
            throw VibeException.forbidden("Only super-admins can promote others to super-admin");
        }
        if (!conv.getParticipantIds().contains(targetUserId)) {
            throw VibeException.badRequest("Target user is not a member");
        }
        conv.getSuperAdmins().put(targetUserId, Instant.now());
        conv.getAdmins().remove(targetUserId); // Upgrade from admin
        Conversation saved = conversationRepository.save(conv);

        websocketTemplate.convertAndSend("/topic/conversation/" + conversationId,
                Map.of("type", "ADMIN_CHANGE", "action", "PROMOTED_SUPER_ADMIN",
                        "userId", targetUserId, "by", actorId));
        return saved;
    }

    /**
     * Appoint an admin with specific privileges.
     * Only super-admins can do this.
     */
    public Conversation appointAdmin(String conversationId, String actorId,
                                     String targetUserId, AdminPrivileges privileges) {
        Conversation conv = getAndValidate(conversationId, actorId);
        if (!conv.isSuperAdmin(actorId)) {
            throw VibeException.forbidden("Only super-admins can appoint admins");
        }
        if (!conv.getParticipantIds().contains(targetUserId)) {
            throw VibeException.badRequest("Target user is not a member");
        }
        privileges.setGrantedAt(Instant.now());
        privileges.setGrantedBy(actorId);
        conv.getAdmins().put(targetUserId, privileges);
        Conversation saved = conversationRepository.save(conv);

        websocketTemplate.convertAndSend("/topic/conversation/" + conversationId,
                Map.of("type", "ADMIN_CHANGE", "action", "APPOINTED_ADMIN",
                        "userId", targetUserId, "by", actorId));
        return saved;
    }

    /**
     * Remove admin / super-admin status.
     * VIBE rule: the CREATOR cannot be demoted by anyone (not even other super-admins).
     */
    public Conversation removeAdmin(String conversationId, String actorId, String targetUserId) {
        Conversation conv = getAndValidate(conversationId, actorId);

        // Creator protection — immutable ownership
        if (conv.isCreator(targetUserId)) {
            throw VibeException.forbidden("The group creator cannot be removed as super-admin");
        }
        if (!conv.isSuperAdmin(actorId)) {
            throw VibeException.forbidden("Only super-admins can remove admins");
        }

        conv.getSuperAdmins().remove(targetUserId);
        conv.getAdmins().remove(targetUserId);
        Conversation saved = conversationRepository.save(conv);

        websocketTemplate.convertAndSend("/topic/conversation/" + conversationId,
                Map.of("type", "ADMIN_CHANGE", "action", "REMOVED_ADMIN",
                        "userId", targetUserId, "by", actorId));
        return saved;
    }

    /**
     * Remove a member from a group.
     * Super-admins can remove anyone. Admins with REMOVE_MEMBERS can remove regular members.
     */
    public void removeMember(String conversationId, String actorId, String targetUserId) {
        Conversation conv = getAndValidate(conversationId, actorId);

        if (conv.isCreator(targetUserId)) {
            throw VibeException.forbidden("Cannot remove the group creator");
        }

        boolean canRemove = conv.isSuperAdmin(actorId) ||
                (conv.isAdmin(actorId) && conv.getAdminPrivileges(actorId) != null
                        && conv.getAdminPrivileges(actorId).isRemoveMembers());

        if (!canRemove) {
            throw VibeException.forbidden("You don't have permission to remove members");
        }

        conv.getParticipantIds().remove(targetUserId);
        conv.getSuperAdmins().remove(targetUserId);
        conv.getAdmins().remove(targetUserId);
        conversationRepository.save(conv);

        websocketTemplate.convertAndSend("/topic/conversation/" + conversationId,
                Map.of("type", "MEMBER_REMOVED", "userId", targetUserId, "by", actorId));
    }

    // ─── Presence ─────────────────────────────────────────────────────────────

    public Map<String, String> getUserPresence(String userId) {
        return presenceService.getPresenceInfo(userId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Conversation getAndValidate(String conversationId, String userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> VibeException.notFound("Conversation"));
        if (!conv.getParticipantIds().contains(userId)) {
            throw VibeException.forbidden("Not a participant");
        }
        return conv;
    }

    private void validateParticipant(String conversationId, String userId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> VibeException.notFound("Conversation"));
        if (!c.getParticipantIds().contains(userId)) {
            throw VibeException.forbidden("Not a participant of this conversation");
        }
    }

    private String buildPreview(Message msg) {
        if (msg.getContent() != null) {
            String c = msg.getContent();
            return c.length() > 100 ? c.substring(0, 100) + "…" : c;
        }
        return switch (msg.getType()) {
            case IMAGE -> "📷 Photo";
            case VIDEO -> "🎥 Video";
            case AUDIO -> "🎵 Audio";
            case FILE  -> "📎 File";
            default    -> "[Media]";
        };
    }

    private void pushDeliveryReceipt(String recipientUserId, String messageId,
                                     String conversationId, DeliveryStatus status) {
        try {
            Map<String, Object> receipt = new HashMap<>();
            receipt.put("type", "DELIVERY_RECEIPT");
            receipt.put("status", status.name());
            receipt.put("conversationId", conversationId);
            if (messageId != null) receipt.put("messageId", messageId);
            // Push to sender's personal receipts queue
            websocketTemplate.convertAndSendToUser(
                    recipientUserId, "/queue/receipts", receipt);
        } catch (Exception e) {
            log.debug("[MSG] Could not push delivery receipt to {}: {}", recipientUserId, e.getMessage());
        }
    }

    private void publishKafkaEvent(String senderId, String conversationId,
                                   String messageId, boolean isGroup) {
        try {
            MessageSentEvent event = MessageSentEvent.builder()
                    .senderId(senderId)
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .isGroupMessage(isGroup)
                    .build();
            kafkaTemplate.send(VibeConstants.TOPIC_MESSAGE_SENT, senderId, event);
        } catch (Exception e) {
            log.warn("[MSG] Kafka publish failed: {}", e.getMessage());
        }
    }
}