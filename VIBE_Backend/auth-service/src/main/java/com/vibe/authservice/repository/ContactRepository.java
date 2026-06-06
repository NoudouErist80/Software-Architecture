package com.vibe.authservice.repository;

import com.vibe.authservice.model.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link Contact} entities.
 *
 * <p>All {@code @Query} methods with named parameters use {@code @Param} so
 * Spring Data JPA can resolve them without the {@code -parameters} javac flag.</p>
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findByOwnerIdAndBlockedFalse(UUID ownerId);

    List<Contact> findByOwnerId(UUID ownerId);

    Optional<Contact> findByOwnerIdAndContactId(UUID ownerId, UUID contactId);

    boolean existsByOwnerIdAndContactId(UUID ownerId, UUID contactId);

    void deleteByOwnerIdAndContactId(UUID ownerId, UUID contactId);

    /**
     * Returns the UUIDs of all users blocked by the given owner.
     *
     * @param ownerId  UUID of the requesting user.
     */
    @Query("SELECT c.contactId FROM Contact c WHERE c.ownerId = :ownerId AND c.blocked = true")
    List<UUID> findBlockedUserIds(@Param("ownerId") UUID ownerId);
}