package com.vibe.roomsservice.service;

import com.vibe.common.constants.VibeConstants;
import com.vibe.common.enums.TokenEarnType;
import com.vibe.common.event.TokenEarnedEvent;
import com.vibe.common.exception.VibeException;
import com.vibe.roomsservice.model.entity.Room;
import com.vibe.roomsservice.model.request.*;
import com.vibe.roomsservice.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomsService {

    private final RoomRepository roomRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessagingTemplate websocket;

    public Room createRoom(String userId, String username, CreateRoomRequest request) {
        Room room = Room.builder()
                .hostId(userId)
                .hostUsername(username)
                .title(request.getTitle())
                .description(request.getDescription())
                .goal(request.getGoal())
                .category(request.getCategory() != null ? request.getCategory() : "GENERAL")
                .language(request.getLanguage() != null ? request.getLanguage() : "en")
                .maxParticipants(request.getMaxParticipants() > 0 ? request.getMaxParticipants() : 100)
                .isPrivate(request.isPrivate())
                .autoVanish(request.getDeadlineAt() != null)
                .vanishAt(request.getDeadlineAt())
                .isLive(true)
                .participantIds(new ArrayList<>(List.of(userId)))
                .coHostIds(new ArrayList<>())
                .createdAt(Instant.now())
                .build();
        room.getParticipantRoles().put(userId, "HOST");

        room = roomRepository.save(room);
        log.info("Room created by {}: {} ({})", username, room.getTitle(), room.getId());

        // Notify via WebSocket
        websocket.convertAndSend("/topic/rooms/new", room);
        return room;
    }

    public Room joinRoom(String userId, String username, String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> VibeException.notFound("Room"));
        if (!room.isLive()) throw VibeException.badRequest("This room has ended");
        if (room.getParticipantIds().size() >= room.getMaxParticipants())
            throw VibeException.badRequest("Room is full");

        if (!room.getParticipantIds().contains(userId)) {
            room.getParticipantIds().add(userId);
            room.getParticipantRoles().put(userId, "LISTENER");
            room.setPeakParticipantCount(Math.max(room.getPeakParticipantCount(),
                    room.getParticipantIds().size()));
        }

        roomRepository.save(room);
        websocket.convertAndSend("/topic/room/" + roomId + "/events",
                Map.of("type", "PARTICIPANT_JOINED", "userId", userId, "username", username));

        // Publish join event for token rewards
        publishSafely(VibeConstants.TOPIC_ROOM_JOINED, userId,
                Map.of("userId", userId, "roomId", roomId, "hostId", room.getHostId()));
        return room;
    }

    public void leaveRoom(String userId, String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> VibeException.notFound("Room"));

        room.getParticipantIds().remove(userId);
        room.getParticipantRoles().remove(userId);

        // If host leaves, promote first co-host or end room
        if (userId.equals(room.getHostId())) {
            if (!room.getCoHostIds().isEmpty()) {
                String newHost = room.getCoHostIds().remove(0);
                room.setHostId(newHost);
                room.getParticipantRoles().put(newHost, "HOST");
            } else if (!room.getParticipantIds().isEmpty()) {
                String newHost = room.getParticipantIds().get(0);
                room.setHostId(newHost);
                room.getParticipantRoles().put(newHost, "HOST");
            } else {
                room.setLive(false);
                room.setEndedAt(Instant.now());
            }
        }

        // Reward host: 2 tokens per participant per 10 minutes
        long minutesLive = ChronoUnit.MINUTES.between(room.getCreatedAt(), Instant.now());
        if (userId.equals(room.getHostId()) && minutesLive >= 10) {
            int bonus = (int)(minutesLive / 10) * room.getPeakParticipantCount()
                        * VibeConstants.TOKEN_EARN_HOST_ROOM_PER_PERSON;
            publishSafely(VibeConstants.TOPIC_TOKEN_EARNED, userId,
                    TokenEarnedEvent.builder()
                            .userId(userId)
                            .tokensEarned(Math.min(bonus, 100))
                            .earnType(TokenEarnType.HOST_ROOM)
                            .referenceId(roomId)
                            .build());
        }

        roomRepository.save(room);
        websocket.convertAndSend("/topic/room/" + roomId + "/events",
                Map.of("type", "PARTICIPANT_LEFT", "userId", userId));
    }

    public Room endRoom(String userId, String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> VibeException.notFound("Room"));
        if (!room.getHostId().equals(userId))
            throw VibeException.forbidden("Only the host can end the room");

        room.setLive(false);
        room.setEndedAt(Instant.now());
        room = roomRepository.save(room);

        websocket.convertAndSend("/topic/room/" + roomId + "/events",
                Map.of("type", "ROOM_ENDED", "roomId", roomId));
        return room;
    }

    public List<Room> getLiveRooms(String category) {
        if (category != null && !category.isBlank())
            return roomRepository.findByIsLiveTrueAndCategoryOrderByCreatedAtDesc(category);
        return roomRepository.findPublicLiveRooms();
    }

    public Room getRoom(String roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> VibeException.notFound("Room"));
    }

    public Room raiseHand(String userId, String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> VibeException.notFound("Room"));
        websocket.convertAndSend("/topic/room/" + roomId + "/events",
                Map.of("type", "HAND_RAISED", "userId", userId));
        return room;
    }

    public Room promoteSpeaker(String hostId, String targetUserId, String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> VibeException.notFound("Room"));
        if (!room.getHostId().equals(hostId))
            throw VibeException.forbidden("Only the host can promote speakers");
        room.getParticipantRoles().put(targetUserId, "SPEAKER");
        room = roomRepository.save(room);
        websocket.convertAndSend("/topic/room/" + roomId + "/events",
                Map.of("type", "SPEAKER_PROMOTED", "userId", targetUserId));
        return room;
    }

    /** Auto-vanish expired rooms every 5 minutes */
    @Scheduled(fixedDelay = 300_000)
    public void vanishExpiredRooms() {
        List<Room> expired = roomRepository.findExpiredRooms(Instant.now());
        expired.forEach(room -> {
            room.setLive(false);
            room.setEndedAt(Instant.now());
            roomRepository.save(room);
            websocket.convertAndSend("/topic/room/" + room.getId() + "/events",
                    Map.of("type", "ROOM_VANISHED", "roomId", room.getId()));
            log.info("Room auto-vanished: {} ({})", room.getTitle(), room.getId());
        });
    }

    private void publishSafely(String topic, String key, Object event) {
        try { kafkaTemplate.send(topic, key, event); }
        catch (Exception e) { log.warn("Kafka publish failed: {}", e.getMessage()); }
    }
}
