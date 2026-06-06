package com.vibe.rewardsservice.repository;

import com.vibe.rewardsservice.model.entity.TokenWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenWalletRepository extends JpaRepository<TokenWallet, UUID> {
    Optional<TokenWallet> findByUserId(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM TokenWallet w WHERE w.userId = :userId")
    Optional<TokenWallet> findByUserIdForUpdate(UUID userId);
}
