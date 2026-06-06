package com.vibe.messagingservice.repository;

import com.vibe.messagingservice.model.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    @Query("{ 'conversationId': ?0, 'hiddenByUserIds': { $nin: [?1] }, 'isDeleted': { $ne: true } }")
    Page<Message> findByConversationIdAndNotHiddenByOrderByCreatedAtDesc(
            String conversationId, String userId, Pageable pageable);

    @Query("{ 'conversationId': ?0, 'readBy': { $nin: [?1] }, 'senderId': { $ne: ?1 }, 'isDeleted': { $ne: true } }")
    List<Message> findUnreadMessages(String conversationId, String userId);

    @Query("{ 'conversationId': ?0, 'createdAt': { $gte: ?1, $lte: ?2 }, 'isDeleted': { $ne: true } }")
    List<Message> findByConversationIdAndCreatedAtBetween(String conversationId, Instant from, Instant to);

    @Query(value = "{ 'conversationId': ?0, 'readBy': { $nin: [?1] }, 'senderId': { $ne: ?1 }, 'isDeleted': { $ne: true } }", count = true)
    long countUnreadMessages(String conversationId, String userId);

    @Query("{ 'conversationId': ?0, 'createdAt': { $gt: ?1 }, 'isDeleted': { $ne: true } }")
    List<Message> findByConversationIdAndCreatedAtAfter(String conversationId, Instant since);
}
