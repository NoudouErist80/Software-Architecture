package com.vibe.messagingservice.controller;

import com.vibe.messagingservice.model.entity.Conversation;
import com.vibe.messagingservice.model.entity.Message;
import com.vibe.messagingservice.model.request.*;
import com.vibe.messagingservice.service.AiSummaryService;
import com.vibe.messagingservice.service.MessagingService;
import com.vibe.messagingservice.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessageController} — both the REST endpoints (delegation
 * to {@link MessagingService}/{@link AiSummaryService}) and the STOMP WebSocket
 * {@code @MessageMapping} handlers (typing, presence, heartbeat, delivery).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageControllerTest {

    @Mock MessagingService messagingService;
    @Mock AiSummaryService aiSummaryService;
    @Mock SimpMessagingTemplate simpMessaging;
    @Mock PresenceService presenceService;
    @InjectMocks MessageController controller;

    private final Principal alice = () -> "user-A";

    // ─── REST endpoints ──────────────────────────────────────────────────────────

    @Test
    void getConversations_delegates() {
        when(messagingService.getConversations("user-A")).thenReturn(List.of());
        var resp = controller.getConversations("user-A");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(messagingService).getConversations("user-A");
    }

    @Test
    void createDirect_returns201() {
        CreateDirectRequest req = new CreateDirectRequest();
        req.setParticipantId("user-B");
        when(messagingService.createDirect(eq("user-A"), eq("alice"), any(), eq(req)))
                .thenReturn(new Conversation());
        var resp = controller.createDirect(req, "user-A", "alice");
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void createGroup_returns201() {
        when(messagingService.createGroup(any(), any(), any())).thenReturn(new Conversation());
        var resp = controller.createGroup(new CreateGroupRequest(), "user-A", "alice");
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void sendMessage_setsConversationIdFromPathAndDelegates() {
        SendMessageRequest req = new SendMessageRequest();
        req.setContent("hi");
        when(messagingService.sendMessage(eq("user-A"), eq("alice"), any(), any())).thenReturn(new Message());

        var resp = controller.sendMessage("conv-1", req, "user-A", "alice");

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(req.getConversationId()).isEqualTo("conv-1");
    }

    @Test
    void markRead_delegates() {
        var resp = controller.markRead("conv-1", "user-A");
        verify(messagingService).markRead("conv-1", "user-A");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void getMessages_delegates() {
        when(messagingService.getMessages(eq("conv-1"), eq("user-A"), eq(0), eq(50)))
                .thenReturn(Page.empty());
        var resp = controller.getMessages("conv-1", 0, 50, "user-A");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void editMessage_delegates() {
        EditMessageRequest req = new EditMessageRequest();
        req.setContent("new");
        when(messagingService.editMessage("m1", "user-A", "new")).thenReturn(new Message());
        var resp = controller.editMessage("m1", req, "user-A");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void deleteMessage_delegates() {
        var resp = controller.deleteMessage("m1", true, "user-A");
        verify(messagingService).deleteMessage("m1", "user-A", true);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void removeMember_delegates() {
        var resp = controller.removeMember("conv-1", "user-C", "user-A");
        verify(messagingService).removeMember("conv-1", "user-A", "user-C");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void getUserPresence_delegates() {
        when(messagingService.getUserPresence("user-B")).thenReturn(Map.of("status", "online"));
        var resp = controller.getUserPresence("user-B", "user-A");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void translateMessage_delegatesToAiService() throws Exception {
        TranslateMessageRequest req = new TranslateMessageRequest();
        req.setTargetLanguage("fr");
        when(aiSummaryService.translateStoredMessage("m1", "fr"))
                .thenReturn(CompletableFuture.completedFuture(new Message()));
        var future = controller.translateMessage("m1", req, "user-A");
        assertThat(future.get().getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void summariseUnread_delegatesToAiService() throws Exception {
        when(aiSummaryService.summariseUnread("conv-1", "user-A", "en"))
                .thenReturn(CompletableFuture.completedFuture("• summary"));
        var future = controller.summariseUnread("conv-1", "en", "user-A");
        assertThat(future.get().getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ─── WebSocket @MessageMapping handlers ──────────────────────────────────────

    @Test
    void handleTyping_nullPrincipal_isIgnored() {
        controller.handleTyping(Map.of("conversationId", "conv-1", "isTyping", true), null);
        verifyNoInteractions(simpMessaging);
    }

    @Test
    void handleTyping_broadcastsTypingEvent() {
        controller.handleTyping(Map.of("conversationId", "conv-1", "isTyping", true), alice);
        verify(simpMessaging).convertAndSend(eq("/topic/conversation/conv-1/typing"), any(Object.class));
    }

    @Test
    void handleHeartbeat_refreshesPresence() {
        controller.handleHeartbeat(alice);
        verify(presenceService).heartbeat("user-A");
    }

    @Test
    void handlePresence_online_marksConnected() {
        controller.handlePresence(Map.of("status", "online"), alice);
        verify(presenceService).userConnected("user-A");
    }

    @Test
    void handleDelivered_delegatesToService() {
        controller.handleDelivered(Map.of("conversationId", "conv-1", "messageIds", List.of("m1")), alice);
        verify(messagingService).markDelivered(eq("conv-1"), eq("user-A"), anyList());
    }
}
