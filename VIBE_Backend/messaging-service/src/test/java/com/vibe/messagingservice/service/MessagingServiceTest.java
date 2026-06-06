package com.vibe.messagingservice.service;

import com.vibe.common.exception.VibeException;
import com.vibe.messagingservice.model.entity.Conversation;
import com.vibe.messagingservice.model.entity.Message;
import com.vibe.messagingservice.model.entity.enums.ConversationType;
import com.vibe.messagingservice.model.entity.enums.DeliveryStatus;
import com.vibe.messagingservice.model.entity.enums.MessageType;
import com.vibe.messagingservice.model.request.CreateDirectRequest;
import com.vibe.messagingservice.model.request.CreateGroupRequest;
import com.vibe.messagingservice.model.request.SendMessageRequest;
import com.vibe.messagingservice.repository.ConversationRepository;
import com.vibe.messagingservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessagingService} — sending, delivery/read receipts,
 * edit/delete, conversation creation, member management and presence. The Mongo
 * repositories, Kafka template, WebSocket template and PresenceService are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessagingServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock ConversationRepository conversationRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock SimpMessagingTemplate websocketTemplate;
    @Mock PresenceService presenceService;
    @InjectMocks MessagingService service;

    private static final String SENDER = "user-A";
    private static final String RECIPIENT = "user-B";
    private Conversation direct;

    @BeforeEach
    void setUp() {
        direct = Conversation.builder()
                .id("conv-1")
                .type(ConversationType.DIRECT)
                .isGroup(false)
                .creatorId(SENDER)
                .participantIds(new ArrayList<>(List.of(SENDER, RECIPIENT)))
                .unreadCounts(new HashMap<>())
                .superAdmins(new HashMap<>())
                .admins(new HashMap<>())
                .build();
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private SendMessageRequest sendReq(String content) {
        SendMessageRequest r = new SendMessageRequest();
        r.setConversationId("conv-1");
        r.setType(MessageType.TEXT);
        r.setContent(content);
        return r;
    }

    // ─── sendMessage ───────────────────────────────────────────────────────────

    @Test
    void sendMessage_success_savesBroadcastsIncrementsUnreadAndPublishesKafka() {
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        when(presenceService.isOnline(anyString())).thenReturn(false);

        Message m = service.sendMessage(SENDER, "alice", null, sendReq("hi"));

        assertThat(m.getContent()).isEqualTo("hi");
        assertThat(m.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        verify(messageRepository).save(any(Message.class));
        verify(websocketTemplate).convertAndSend(eq("/topic/conversation/conv-1"), any(Object.class));
        verify(websocketTemplate).convertAndSendToUser(eq(RECIPIENT), eq("/queue/messages"), any());
        verify(kafkaTemplate).send(anyString(), eq(SENDER), any());
        assertThat(direct.getUnreadCounts().get(RECIPIENT)).isEqualTo(1);
    }

    @Test
    void sendMessage_onlineRecipient_transitionsToDelivered() {
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        when(presenceService.isOnline(RECIPIENT)).thenReturn(true);

        Message m = service.sendMessage(SENDER, "alice", null, sendReq("hi"));

        assertThat(m.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void sendMessage_conversationNotFound_throws() {
        when(conversationRepository.findById(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.sendMessage(SENDER, "a", null, sendReq("hi")))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void sendMessage_notParticipant_throwsForbidden() {
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        assertThatThrownBy(() -> service.sendMessage("stranger", "s", null, sendReq("hi")))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("Not a participant");
    }

    // ─── createDirect / createGroup ──────────────────────────────────────────────

    @Test
    void createDirect_existingConversation_isReturnedWithoutSaving() {
        when(conversationRepository.findDirectBetween(SENDER, RECIPIENT)).thenReturn(Optional.of(direct));
        CreateDirectRequest req = new CreateDirectRequest();
        req.setParticipantId(RECIPIENT);

        Conversation c = service.createDirect(SENDER, "alice", null, req);

        assertThat(c).isSameAs(direct);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createDirect_new_storesBothParticipantsAndUsernames() {
        when(conversationRepository.findDirectBetween(anyString(), anyString())).thenReturn(Optional.empty());
        CreateDirectRequest req = new CreateDirectRequest();
        req.setParticipantId(RECIPIENT);
        req.setParticipantUsername("bob");

        Conversation c = service.createDirect(SENDER, "alice", null, req);

        assertThat(c.getParticipantIds()).containsExactly(SENDER, RECIPIENT);
        assertThat(c.getParticipantUsernames()).containsExactly("alice", "bob");
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void createGroup_addsCreatorAndMakesThemSuperAdmin() {
        CreateGroupRequest req = new CreateGroupRequest();
        req.setName("Squad");
        req.setParticipantIds(new ArrayList<>(List.of(RECIPIENT)));

        Conversation c = service.createGroup(SENDER, "alice", req);

        assertThat(c.getName()).isEqualTo("Squad");
        assertThat(c.getParticipantIds()).contains(SENDER, RECIPIENT);
        assertThat(c.isSuperAdmin(SENDER)).isTrue();
    }

    // ─── markRead / markDelivered ────────────────────────────────────────────────

    @Test
    void markRead_clearsUnread_marksMessagesRead_andNotifiesSender() {
        direct.getUnreadCounts().put(RECIPIENT, 3);
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .readBy(new ArrayList<>()).deliveryStatus(DeliveryStatus.SENT).build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        when(messageRepository.findUnreadMessages("conv-1", RECIPIENT)).thenReturn(List.of(msg));

        service.markRead("conv-1", RECIPIENT);

        assertThat(direct.getUnreadCounts().get(RECIPIENT)).isZero();
        assertThat(msg.getReadBy()).contains(RECIPIENT);
        assertThat(msg.getDeliveryStatus()).isEqualTo(DeliveryStatus.READ);
        verify(websocketTemplate).convertAndSend(eq("/topic/conversation/conv-1/read"), any(Object.class));
    }

    @Test
    void markDelivered_addsRecipientAndNotifiesSender() {
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .deliveredTo(new ArrayList<>()).deliveryStatus(DeliveryStatus.SENT).build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        when(messageRepository.findById("m1")).thenReturn(Optional.of(msg));

        service.markDelivered("conv-1", RECIPIENT, List.of("m1"));

        assertThat(msg.getDeliveredTo()).contains(RECIPIENT);
        verify(messageRepository).save(msg);
    }

    // ─── editMessage ─────────────────────────────────────────────────────────────

    @Test
    void editMessage_success_updatesContentAndBroadcasts() {
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .content("old").createdAt(Instant.now()).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(msg));

        Message edited = service.editMessage("m1", SENDER, "new content");

        assertThat(edited.getContent()).isEqualTo("new content");
        assertThat(edited.isEdited()).isTrue();
        verify(websocketTemplate).convertAndSend(eq("/topic/conversation/conv-1"), any(Object.class));
    }

    @Test
    void editMessage_byNonOwner_throwsForbidden() {
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .content("old").createdAt(Instant.now()).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service.editMessage("m1", "someone-else", "x"))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void editMessage_afterWindowExpired_throws() {
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .content("old").createdAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service.editMessage("m1", SENDER, "x"))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("window");
    }

    // ─── deleteMessage ───────────────────────────────────────────────────────────

    @Test
    void deleteMessage_forEveryone_marksDeletedAndBroadcasts() {
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .content("hi").createdAt(Instant.now()).hiddenByUserIds(new ArrayList<>()).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(msg));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));

        service.deleteMessage("m1", SENDER, true);

        assertThat(msg.isDeleted()).isTrue();
        verify(websocketTemplate).convertAndSend(eq("/topic/conversation/conv-1"), any(Object.class));
    }

    @Test
    void deleteMessage_forMeOnly_hidesForUser() {
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .content("hi").createdAt(Instant.now()).hiddenByUserIds(new ArrayList<>()).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(msg));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));

        service.deleteMessage("m1", SENDER, false);

        assertThat(msg.getHiddenByUserIds()).contains(SENDER);
        assertThat(msg.isDeleted()).isFalse();
    }

    @Test
    void deleteMessage_byNonOwner_throwsForbidden() {
        Message msg = Message.builder().id("m1").conversationId("conv-1").senderId(SENDER)
                .content("hi").createdAt(Instant.now()).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(msg));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));

        assertThatThrownBy(() -> service.deleteMessage("m1", RECIPIENT, true))
                .isInstanceOf(VibeException.class);
    }

    // ─── getMessages / getConversations / presence ───────────────────────────────

    @Test
    void getMessages_validatesParticipant() {
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        service.getMessages("conv-1", SENDER, 0, 50);
        verify(messageRepository).findByConversationIdAndNotHiddenByOrderByCreatedAtDesc(
                eq("conv-1"), eq(SENDER), any());
    }

    @Test
    void getMessages_notParticipant_throws() {
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        assertThatThrownBy(() -> service.getMessages("conv-1", "stranger", 0, 50))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void getConversations_delegatesToRepository() {
        when(conversationRepository.findByParticipantIdsContainingOrderByLastMessageAtDesc(SENDER))
                .thenReturn(List.of(direct));
        assertThat(service.getConversations(SENDER)).hasSize(1);
    }

    @Test
    void getUserPresence_delegatesToPresenceService() {
        when(presenceService.getPresenceInfo("user-X")).thenReturn(Map.of("status", "online"));
        assertThat(service.getUserPresence("user-X")).containsEntry("status", "online");
    }

    // ─── removeMember ────────────────────────────────────────────────────────────

    private Conversation group() {
        return Conversation.builder()
                .id("grp-1").type(ConversationType.GROUP).isGroup(true).creatorId(SENDER)
                .participantIds(new ArrayList<>(List.of(SENDER, RECIPIENT, "user-C")))
                .superAdmins(new HashMap<>(Map.of(SENDER, Instant.now())))
                .admins(new HashMap<>())
                .unreadCounts(new HashMap<>())
                .build();
    }

    @Test
    void removeMember_bySuperAdmin_removesAndBroadcasts() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));

        service.removeMember("grp-1", SENDER, "user-C");

        assertThat(g.getParticipantIds()).doesNotContain("user-C");
        verify(websocketTemplate).convertAndSend(eq("/topic/conversation/grp-1"), any(Object.class));
    }

    @Test
    void removeMember_cannotRemoveCreator() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.removeMember("grp-1", SENDER, SENDER))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("creator");
    }

    @Test
    void removeMember_byNonAdmin_throwsForbidden() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));

        // RECIPIENT is a plain member → no permission to remove user-C
        assertThatThrownBy(() -> service.removeMember("grp-1", RECIPIENT, "user-C"))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("permission");
    }

    // ─── createCommunity + admin management + overloads ──────────────────────────

    @Test
    void createCommunity_createsCommunityAndAnnouncementsChannel() {
        CreateGroupRequest req = new CreateGroupRequest();
        req.setName("My Community");

        Conversation community = service.createCommunity(SENDER, "alice", req);

        assertThat(community.getType()).isEqualTo(ConversationType.COMMUNITY);
        assertThat(community.isSuperAdmin(SENDER)).isTrue();
        // community + announcements channel + re-save = at least 2 saves
        verify(conversationRepository, atLeast(2)).save(any(Conversation.class));
    }

    @Test
    void promoteSuperAdmin_bySuperAdmin_succeeds() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));
        service.promoteSuperAdmin("grp-1", SENDER, RECIPIENT);
        assertThat(g.isSuperAdmin(RECIPIENT)).isTrue();
    }

    @Test
    void promoteSuperAdmin_byNonSuperAdmin_throws() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.promoteSuperAdmin("grp-1", RECIPIENT, "user-C"))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void promoteSuperAdmin_targetNotMember_throws() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.promoteSuperAdmin("grp-1", SENDER, "outsider"))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void appointAdmin_bySuperAdmin_succeeds() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));
        Conversation.AdminPrivileges priv = Conversation.AdminPrivileges.builder().removeMembers(true).build();

        service.appointAdmin("grp-1", SENDER, RECIPIENT, priv);

        assertThat(g.isAdmin(RECIPIENT)).isTrue();
    }

    @Test
    void appointAdmin_byNonSuperAdmin_throws() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));
        Conversation.AdminPrivileges priv = Conversation.AdminPrivileges.builder().build();
        assertThatThrownBy(() -> service.appointAdmin("grp-1", RECIPIENT, "user-C", priv))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void removeAdmin_bySuperAdmin_succeeds() {
        Conversation g = group();
        g.getAdmins().put(RECIPIENT, Conversation.AdminPrivileges.builder().build());
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));

        service.removeAdmin("grp-1", SENDER, RECIPIENT);

        assertThat(g.isAdmin(RECIPIENT)).isFalse();
    }

    @Test
    void removeAdmin_cannotRemoveCreator() {
        Conversation g = group();
        when(conversationRepository.findById("grp-1")).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.removeAdmin("grp-1", SENDER, SENDER))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("creator");
    }

    @Test
    void sendMessage_threeArgOverload_delegates() {
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));
        when(presenceService.isOnline(anyString())).thenReturn(false);
        Message m = service.sendMessage(SENDER, "alice", sendReq("hello"));
        assertThat(m.getContent()).isEqualTo("hello");
    }

    @Test
    void createDirect_threeArgOverload_delegates() {
        when(conversationRepository.findDirectBetween(anyString(), anyString())).thenReturn(Optional.empty());
        CreateDirectRequest req = new CreateDirectRequest();
        req.setParticipantId(RECIPIENT);
        assertThat(service.createDirect(SENDER, "alice", req)).isNotNull();
    }
}
