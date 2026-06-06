package com.vibe.authservice.repository;

import com.vibe.authservice.model.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link User} entities.
 *
 * <p><strong>All @Query methods that use named parameters MUST annotate every
 * parameter with {@code @Param}</strong>.  Without the {@code -parameters}
 * javac flag (not enabled in this project's pom.xml), Spring Data JPA cannot
 * resolve parameter names at runtime and throws
 * {@code InvalidDataAccessApiUsageException}.  Adding {@code @Param} is the
 * portable, compiler-flag-independent solution and is the industry standard.</p>
 *
 * <p>Optimised for high-volume global workloads:
 * <ul>
 *   <li>Bulk update queries avoid loading the full entity into the persistence
 *       context — only the modified columns are written to the DB.</li>
 *   <li>Derived-query finders (findByEmail, etc.) rely on Hibernate's
 *       query-plan cache — no JPQL string is re-parsed per call.</li>
 * </ul>
 * </p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // ─── Lookup finders ───────────────────────────────────────────────────────

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Fuzzy phone lookup: finds the first active user whose stored phone number
     * ends with the provided suffix.  This makes lookup resilient to country-code
     * formatting differences — e.g. searching "677588867" matches "+237677588867".
     *
     * The query is index-friendly on most databases when the suffix is long
     * (≥7 digits), which covers all real-world phone searches.
     *
     * @param suffix  The trailing digits supplied by the caller (digits only,
     *                no leading "+", spaces, or dashes).
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.phoneNumber LIKE CONCAT('%', :suffix)")
    Optional<User> findByPhoneNumberSuffix(@Param("suffix") String suffix);

    // ─── Existence checks ─────────────────────────────────────────────────────

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByPhoneNumber(String phoneNumber);

    // ─── Search ───────────────────────────────────────────────────────────────

    List<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String username, String fullName, Pageable pageable);

    /**
     * Fetch country-based friend suggestions, excluding already-known users.
     *
     * @param countryCode  ISO-2 country code of the requesting user.
     * @param excludeIds   UUIDs to exclude (self + existing contacts).
     * @param pageable     pagination / limit control.
     */
    @Query("SELECT u FROM User u WHERE u.countryCode = :countryCode " +
           "AND u.id NOT IN :excludeIds AND u.isActive = true")
    List<User> findSuggestions(
            @Param("countryCode") String countryCode,
            @Param("excludeIds")  List<UUID> excludeIds,
            Pageable pageable);

    // ─── Bulk update queries (no entity load required) ────────────────────────

    /**
     * Records a successful login: updates last-login timestamp and resets the
     * failed-attempts counter in a single UPDATE statement.
     *
     * @param id  user UUID.
     * @param now current server time.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :now, u.failedLoginAttempts = 0 WHERE u.id = :id")
    void updateLastLogin(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Increments the failed-login counter without loading the user entity.
     *
     * @param id  user UUID.
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    void incrementFailedAttempts(@Param("id") UUID id);

    /**
     * Updates streak counters.
     *
     * @param id   user UUID.
     * @param days new streak day count.
     * @param date timestamp of this streak action.
     */
    @Modifying
    @Query("UPDATE User u SET u.streakDays = :days, u.lastStreakDate = :date WHERE u.id = :id")
    void updateStreak(@Param("id") UUID id, @Param("days") int days, @Param("date") Instant date);

    /**
     * Stores a new profile-picture URL without touching other columns.
     *
     * @param id  user UUID.
     * @param url CDN URL of the new profile picture.
     */
    @Modifying
    @Query("UPDATE User u SET u.profilePictureUrl = :url WHERE u.id = :id")
    void updateProfilePicture(@Param("id") UUID id, @Param("url") String url);
}