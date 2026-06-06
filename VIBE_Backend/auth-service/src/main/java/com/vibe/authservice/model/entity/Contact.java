package com.vibe.authservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts",
    indexes = {
        @Index(name = "idx_contacts_owner", columnList = "owner_id"),
        @Index(name = "idx_contacts_contact", columnList = "contact_id"),
        @Index(name = "idx_contacts_unique", columnList = "owner_id, contact_id", unique = true)
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Builder.Default
    @Column(name = "is_blocked")
    private boolean blocked = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
