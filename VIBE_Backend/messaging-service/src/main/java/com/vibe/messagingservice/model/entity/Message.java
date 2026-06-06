package com.vibe.messagingservice.model.entity;

import com.vibe.messagingservice.model.entity.enums.DeliveryStatus;
import com.vibe.messagingservice.model.entity.enums.MessageType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;

/**
 * Core message document for VIBE Messenger.
 *
 * <h3>Delivery / read-receipt lifecycle (WhatsApp parity)</h3>
 * <pre>
 *   SENT      — saved to MongoDB, pushed to /topic/conversation/{id}
 *   DELIVERED — recipient's client ACKs receipt (they may be offline → delivered on reconnect)
 *   READ      — recipient opens conversation and marks read
 * </pre>
 *
 * <h3>Offline message delivery</h3>
 * Messages are persisted immediately on send.  When an offline recipient comes
 * back online, the frontend fetches conversation history via REST
 * (GET /messaging/conversations/{id}/messages) — no message is ever lost.
 *
 * <h3>Phone-number sender identification</h3>
 * {@code senderPhone} allows unknown-contact scenarios: a recipient who hasn't
 * added the sender as a contact will see the phone number (WhatsApp UX).
 */
@Document(collection = "messages")
@CompoundIndex(def = "{'conversationId': 1, 'createdAt': -1}")
@CompoundIndex(def = "{'conversationId': 1, 'deliveryStatus': 1}")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Message {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    /** Auth-service user UUID */
    private String senderId;

    /** @username — displayed in group chats to identify the sender */
    private String senderUsername;

    /**
     * Sender's phone number (E.164 format, e.g. +237612345678).
     * Shown to recipients who haven't added this user as a contact — exactly
     * like WhatsApp's "Message from unknown number" display.
     */
    private String senderPhone;

    private MessageType type;
    private String content;
    private String mediaUrl;
    private String thumbnailUrl;
    private String detectedLanguage;

    /**
     * Current delivery status for DM conversations.
     * SENT → DELIVERED → READ
     * For group messages, per-member status is tracked in {@code readBy}.
     */
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.SENT;

    /**
     * Timestamp when at least one recipient received the message
     * (i.e. came online and the client ACKed receipt).
     */
    private Instant deliveredAt;

    /**
     * Timestamp when the first recipient read the message.
     * For DMs: shown as the "seen" time.
     * For groups: shown when hovered ("Read by X people").
     */
    private Instant readAt;

    /** Translations: languageCode → translated text */
    @Builder.Default
    private Map<String, String> translations = new HashMap<>();

    private boolean isEdited;
    private boolean isDeleted;
    private String  replyToMessageId;

    /** Emoji reactions: emoji → count */
    @Builder.Default
    private Map<String, Integer> reactions = new HashMap<>();

    /** Emoji reactions: emoji → [userId, ...] (for deduplication) */
    @Builder.Default
    private Map<String, List<String>> reactionUsers = new HashMap<>();

    /**
     * User IDs of everyone who has read this message.
     * For groups: use this list to show "seen by" count.
     * For DMs: non-empty means READ status.
     */
    @Builder.Default
    private List<String> readBy = new ArrayList<>();

    /**
     * User IDs of everyone who has received (DELIVERED) this message.
     * Populated when the recipient's client sends a STOMP delivery ACK.
     */
    @Builder.Default
    private List<String> deliveredTo = new ArrayList<>();

    /** Users who soft-deleted this message for themselves only */
    @Builder.Default
    private List<String> hiddenByUserIds = new ArrayList<>();

    /** Whether this is a forwarded message */
    private boolean isForwarded;

    /** Original message ID if forwarded */
    private String forwardedFromMessageId;

    @CreatedDate
    private Instant createdAt;

    private Instant editedAt;

    // ─── Computed helpers ─────────────────────────────────────────────────────

    /**
     * Returns true if all given recipient IDs have read this message.
     * Used to transition a DM message to READ status.
     */
    public boolean isReadByAll(List<String> recipientIds) {
        return readBy.containsAll(recipientIds);
    }

    /**
     * Returns true if all given recipient IDs have received this message.
     */
    public boolean isDeliveredToAll(List<String> recipientIds) {
        return deliveredTo.containsAll(recipientIds);
    }
}
