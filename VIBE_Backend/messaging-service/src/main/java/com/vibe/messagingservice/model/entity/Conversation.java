package com.vibe.messagingservice.model.entity;

import com.vibe.messagingservice.model.entity.enums.ConversationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;

/**
 * Represents a conversation in VIBE Messenger.
 *
 * <h3>Types</h3>
 * <ul>
 *   <li>DIRECT   — 1-to-1 private chat</li>
 *   <li>GROUP    — group chat (up to 500 members)</li>
 *   <li>COMMUNITY — a "space" containing multiple sub-groups (WhatsApp Communities)</li>
 * </ul>
 *
 * <h3>VIBE Admin Hierarchy</h3>
 * <pre>
 *   SUPER_ADMIN (creator)
 *     ├── Can promote members to SUPER_ADMIN (shared ownership)
 *     ├── Can appoint ADMIN with custom privilege set
 *     ├── Can revoke any ADMIN or SUPER_ADMIN (except self if sole super-admin)
 *     └── Cannot be removed by admins
 *
 *   SUPER_ADMIN (promoted)
 *     └── Same as creator, except creator retains absolute veto power
 *
 *   ADMIN
 *     └── Has the specific privileges granted by a SUPER_ADMIN:
 *           DELETE_MESSAGES, REMOVE_MEMBERS, PIN_MESSAGES, SEND_ANNOUNCEMENTS,
 *           MANAGE_MEMBERS, EDIT_GROUP_INFO, APPROVE_JOIN_REQUESTS
 *
 *   MEMBER — default role
 * </pre>
 *
 * <h3>Community model</h3>
 * A Community is a top-level Conversation of type COMMUNITY.  It contains
 * references to sub-group Conversation IDs.  Members of the community are
 * automatically members of the "Announcements" channel but must be explicitly
 * added to sub-groups.
 */
@Document(collection = "conversations")
@CompoundIndex(def = "{'participantIds': 1, 'lastMessageAt': -1}")
@CompoundIndex(def = "{'communityId': 1}")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Conversation {

    @Id
    private String id;

    /**
     * DIRECT, GROUP, or COMMUNITY.
     * {@code isGroup} is preserved for backward compat — use {@code type} for new logic.
     */
    @Builder.Default
    private ConversationType type = ConversationType.DIRECT;

    /** For backward compat — true when type is GROUP or COMMUNITY */
    private boolean isGroup;

    /** Explicit name — set for groups/communities; null for direct chats (by design). */
    private String name;

    private String description;
    private String avatarUrl;

    /**
     * The original creator. Can never be stripped of SUPER_ADMIN by anyone.
     * This is VIBE's unique "Founding Admin" concept — immortal ownership.
     */
    private String creatorId;

    /** All participant user IDs */
    @Indexed
    @Builder.Default
    private List<String> participantIds = new ArrayList<>();

    /**
     * Parallel list of usernames for display (avoids auth-service round-trips).
     * WhatsApp Web / Messenger pattern.
     */
    @Builder.Default
    private List<String> participantUsernames = new ArrayList<>();

    /**
     * Parallel list of phone numbers (E.164 format).
     * Enables unknown-contact identification like WhatsApp.
     */
    @Builder.Default
    private List<String> participantPhones = new ArrayList<>();

    // ─── Admin / Role System ──────────────────────────────────────────────────

    /**
     * Super admins: userId → Instant of promotion.
     * The creator's userId is always in this map from creation.
     * Super admins have all privileges including promoting/demoting others.
     */
    @Builder.Default
    private Map<String, Instant> superAdmins = new HashMap<>();

    /**
     * Regular admins: userId → AdminPrivileges.
     * Privileges are granted by a super admin and can be customized.
     */
    @Builder.Default
    private Map<String, AdminPrivileges> admins = new HashMap<>();

    // ─── Community Support ────────────────────────────────────────────────────

    /**
     * If this conversation is a sub-group of a community, this is the parent
     * community's conversation ID.
     */
    private String communityId;

    /**
     * If this conversation IS a community (type=COMMUNITY), these are the IDs
     * of its sub-group conversations.
     */
    @Builder.Default
    private List<String> subGroupIds = new ArrayList<>();

    /** Whether this is the community's "Announcements" channel (read-only for members) */
    private boolean isAnnouncementsChannel;

    /** Join link for the community/group (short-code) */
    private String inviteCode;

    /** Whether new members need admin approval */
    private boolean requiresApproval;

    /** Pending join requests: userId → Instant of request */
    @Builder.Default
    private Map<String, Instant> pendingJoinRequests = new HashMap<>();

    /** Maximum members (default 500 for groups, 5000 for communities) */
    @Builder.Default
    private int maxMembers = 500;

    // ─── Message metadata ─────────────────────────────────────────────────────

    private String  lastMessageId;
    private String  lastMessagePreview;
    private Instant lastMessageAt;

    /**
     * Per-user unread message count: userId → count.
     * Reset to 0 on markRead.
     */
    @Builder.Default
    private Map<String, Integer> unreadCounts = new HashMap<>();

    // ─── AI Fields ────────────────────────────────────────────────────────────

    private String  aiSummary;
    private Instant aiSummaryGeneratedAt;
    private int     aiSummaryMessageCount;

    // ─── Misc ─────────────────────────────────────────────────────────────────

    private boolean isActive;
    private boolean isMuted;

    /** Users who have muted this conversation: userId → unmute Instant (null = permanent) */
    @Builder.Default
    private Map<String, Instant> mutedBy = new HashMap<>();

    @LastModifiedDate
    private Instant updatedAt;
    private Instant createdAt;

    // ─── Inner class: AdminPrivileges ─────────────────────────────────────────

    /**
     * Granular privilege set granted to a regular admin.
     *
     * <p>VIBE UNIQUE: Privileges are granted per-admin, allowing fine-grained
     * control. A super-admin can give one admin deletion rights but not
     * member-removal rights, for example.
     */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdminPrivileges {
        /** Can delete any message in the group */
        @Builder.Default private boolean deleteMessages     = false;
        /** Can remove members from the group */
        @Builder.Default private boolean removeMembers      = false;
        /** Can pin/unpin messages */
        @Builder.Default private boolean pinMessages        = false;
        /** Can send to announcement-only channels */
        @Builder.Default private boolean sendAnnouncements  = false;
        /** Can add members or approve join requests */
        @Builder.Default private boolean manageMembers      = false;
        /** Can edit group name, description, and avatar */
        @Builder.Default private boolean editGroupInfo      = false;
        /** Custom title shown next to the admin's name (e.g. "Moderator", "Coach") */
        private String customTitle;
        /** Instant when this admin role was granted */
        private Instant grantedAt;
        /** Which super-admin granted this role */
        private String grantedBy;
    }

    // ─── Computed helpers ─────────────────────────────────────────────────────

    public boolean isSuperAdmin(String userId) {
        return creatorId.equals(userId) || superAdmins.containsKey(userId);
    }

    public boolean isAdmin(String userId) {
        return isSuperAdmin(userId) || admins.containsKey(userId);
    }

    public boolean isCreator(String userId) {
        return creatorId.equals(userId);
    }

    public AdminPrivileges getAdminPrivileges(String userId) {
        if (isSuperAdmin(userId)) {
            // Super admins have all privileges
            return AdminPrivileges.builder()
                    .deleteMessages(true).removeMembers(true).pinMessages(true)
                    .sendAnnouncements(true).manageMembers(true).editGroupInfo(true)
                    .build();
        }
        return admins.get(userId);
    }
}
