package com.vibe.rewardsservice.model.entity;

import com.vibe.rewardsservice.model.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cashout_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashoutRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private int tokenAmount;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal xafAmount;
    @Enumerated(EnumType.STRING)
    private CashoutProvider provider;
    private String phoneNumber;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CashoutStatus status = CashoutStatus.PENDING;
    private String externalTransactionId;
    private String failureReason;
    private Instant processedAt;
    private Instant createdAt;
}
