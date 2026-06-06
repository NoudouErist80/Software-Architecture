package com.vibe.authservice.model.entity;

import com.vibe.common.enums.Language;
import com.vibe.common.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Core user entity. Stores authentication credentials and basic profile.
 * Rich profile data (bio, avatar, social stats) lives in feed-service.
 */
@Entity
@Table(name = "users",
    indexes = {
        @Index(name = "idx_users_email",    columnList = "email",    unique = true),
        @Index(name = "idx_users_username", columnList = "username", unique = true),
        @Index(name = "idx_users_phone",    columnList = "phone_number")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "country_code", length = 5)
    @Builder.Default
    private String countryCode = "CM";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private Language preferredLanguage = Language.FRENCH;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_email_verified")
    @Builder.Default
    private boolean isEmailVerified = false;

    @Column(name = "is_phone_verified")
    @Builder.Default
    private boolean isPhoneVerified = false;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "streak_days")
    @Builder.Default
    private int streakDays = 0;

    @Column(name = "last_streak_date")
    private Instant lastStreakDate;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
