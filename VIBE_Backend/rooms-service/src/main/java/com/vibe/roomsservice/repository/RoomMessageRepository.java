package com.vibe.roomsservice.repository;

import com.vibe.roomsservice.model.entity.RoomMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomMessageRepository extends MongoRepository<RoomMessage, String> {
    Page<RoomMessage> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
    long countByRoomId(String roomId);
}
