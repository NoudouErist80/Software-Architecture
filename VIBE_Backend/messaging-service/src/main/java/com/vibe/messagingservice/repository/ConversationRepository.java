package com.vibe.messagingservice.repository;

import com.vibe.messagingservice.model.entity.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByParticipantIdsContainingOrderByLastMessageAtDesc(String userId);

    @Query("{ 'type': 'DIRECT', 'participantIds': { $all: [?0, ?1] } }")
    Optional<Conversation> findDirectBetween(String userId1, String userId2);

    @Query("{ 'communityId': ?0 }")
    List<Conversation> findByCommunityId(String communityId);

    @Query("{ 'participantIds': ?0, 'type': { $in: ['GROUP', 'COMMUNITY'] } }")
    List<Conversation> findGroupsByParticipant(String userId);
}
