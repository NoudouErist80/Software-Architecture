package com.vibe.feedservice.repository;

import com.vibe.feedservice.model.entity.Post;
import com.vibe.feedservice.model.entity.enums.MoodTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    Page<Post> findByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(String authorId, Pageable pageable);
    Page<Post> findByIsActiveTrueAndMoodTagsContainingOrderByCreatedAtDesc(MoodTag mood, Pageable pageable);
}
