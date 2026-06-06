package com.vibe.rewardsservice.model.entity;

import com.vibe.common.enums.TokenEarnType;
import com.vibe.rewardsservice.model.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_transactions",
    indexes = {
        @Index(name = "idx_tx_user", columnList = "user_id"),
        @Index(name = "idx_tx_created", columnList = "created_at")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TokenTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private int amount;
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    @Enumerated(EnumType.STRING)
    private TokenEarnType earnType;
    private String referenceId;
    private String description;
    private long balanceAfter;
    private Instant createdAt;
}
