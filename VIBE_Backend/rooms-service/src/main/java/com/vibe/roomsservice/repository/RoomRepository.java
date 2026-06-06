package com.vibe.roomsservice.repository;

import com.vibe.roomsservice.model.entity.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {

    List<Room> findByIsLiveTrueOrderByParticipantIdsDesc();

    @Query("{ 'isLive': true, 'isPrivate': false }")
    List<Room> findPublicLiveRooms();

    @Query("{ 'participantIds': ?0, 'isLive': true }")
    List<Room> findActiveByParticipant(String userId);

    List<Room> findByHostIdOrderByCreatedAtDesc(String hostId);

    List<Room> findByIsLiveTrueAndCategoryOrderByCreatedAtDesc(String category);

    @Query("{ 'vanishAt': { $lt: ?0 }, 'isLive': true }")
    List<Room> findExpiredRooms(Instant now);
}
