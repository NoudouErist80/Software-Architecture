package com.vibe.feedservice.repository;

import com.vibe.feedservice.model.entity.Status;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StatusRepository extends MongoRepository<Status, String> {
    List<Status> findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(String authorId);

    @Query("{ 'authorId': { $in: ?0 }, 'isActive': true }")
    List<Status> findByAuthorIdInAndIsActiveTrue(List<String> authorIds);

    List<Status> findByIsActiveTrueOrderByCreatedAtDesc();
}
