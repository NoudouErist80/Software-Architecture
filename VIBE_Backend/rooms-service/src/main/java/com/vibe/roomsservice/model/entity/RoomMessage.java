package com.vibe.roomsservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "room_messages")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomMessage {

    @Id
    private String id;

    @Indexed
    private String roomId;

    private String senderId;
    private String senderUsername;
    private String content;
    private String type;   // TEXT, REACTION, SYSTEM

    @Builder.Default
    private Map<String, Integer> reactions = new HashMap<>();

    @CreatedDate
    private Instant createdAt;
}
