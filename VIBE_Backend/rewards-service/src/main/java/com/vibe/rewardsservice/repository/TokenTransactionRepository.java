package com.vibe.rewardsservice.repository;

import com.vibe.rewardsservice.model.entity.TokenTransaction;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, UUID> {
    Page<TokenTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
