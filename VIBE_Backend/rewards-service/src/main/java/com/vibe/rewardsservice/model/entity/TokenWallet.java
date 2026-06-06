package com.vibe.rewardsservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_wallets",
    indexes = @Index(name = "idx_wallet_user", columnList = "user_id", unique = true))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TokenWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Builder.Default
    private long balance = 0L;
    @Builder.Default
    private long totalEarned = 0L;
    @Builder.Default
    private long totalRedeemed = 0L;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
