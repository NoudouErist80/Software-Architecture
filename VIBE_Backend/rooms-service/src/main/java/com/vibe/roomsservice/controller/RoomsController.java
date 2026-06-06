package com.vibe.roomsservice.controller;

import com.vibe.common.response.ApiResponse;
import com.vibe.roomsservice.model.request.CreateRoomRequest;
import com.vibe.roomsservice.service.RoomsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "VIBE contextual rooms — create, join, live, auto-vanish")
public class RoomsController {

    private final RoomsService roomsService;

    @PostMapping
    @Operation(summary = "Create a new contextual room with optional goal and deadline")
    public ResponseEntity<ApiResponse<Object>> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created!", roomsService.createRoom(userId, username, request)));
    }

    @GetMapping
    @Operation(summary = "Get all live public rooms (optional ?category=STUDY)")
    public ResponseEntity<ApiResponse<Object>> getLiveRooms(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.success(roomsService.getLiveRooms(category)));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get room details")
    public ResponseEntity<ApiResponse<Object>> getRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.success(roomsService.getRoom(roomId)));
    }

    @PostMapping("/{roomId}/join")
    @Operation(summary = "Join a live room")
    public ResponseEntity<ApiResponse<Object>> joinRoom(
            @PathVariable String roomId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Username") String username) {
        return ResponseEntity.ok(ApiResponse.success("Joined room!", roomsService.joinRoom(userId, username, roomId)));
    }

    @PostMapping("/{roomId}/leave")
    @Operation(summary = "Leave a room")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable String roomId,
            @RequestHeader("X-User-Id") String userId) {
        roomsService.leaveRoom(userId, roomId);
        return ResponseEntity.ok(ApiResponse.success("Left room", null));
    }

    @PostMapping("/{roomId}/end")
    @Operation(summary = "End the room (host only)")
    public ResponseEntity<ApiResponse<Object>> endRoom(
            @PathVariable String roomId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success("Room ended", roomsService.endRoom(userId, roomId)));
    }

    @PostMapping("/{roomId}/raise-hand")
    @Operation(summary = "Raise hand to speak in a room")
    public ResponseEntity<ApiResponse<Object>> raiseHand(
            @PathVariable String roomId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(roomsService.raiseHand(userId, roomId)));
    }

    @PostMapping("/{roomId}/promote/{targetUserId}")
    @Operation(summary = "Promote a listener to speaker (host only)")
    public ResponseEntity<ApiResponse<Object>> promoteSpeaker(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                roomsService.promoteSpeaker(userId, targetUserId, roomId)));
    }
}
