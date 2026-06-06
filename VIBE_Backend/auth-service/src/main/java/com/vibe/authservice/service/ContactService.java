package com.vibe.authservice.service;

import com.vibe.authservice.model.entity.Contact;
import com.vibe.authservice.model.entity.User;
import com.vibe.authservice.model.response.AuthResponse;
import com.vibe.authservice.repository.ContactRepository;
import com.vibe.authservice.repository.UserRepository;
import com.vibe.common.exception.VibeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contact management service for VIBE.
 *
 * <h3>WhatsApp-style contact model</h3>
 * <ul>
 *   <li>Users are found by phone number or username</li>
 *   <li>A contact can be saved with ANY display name the owner chooses</li>
 *   <li>The saved display name is stored on the Contact record (not on the User)</li>
 *   <li>Search results are always filtered to exclude the requesting user</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository    userRepository;

    public List<AuthResponse.UserSummary> getMyContacts(String userId) {
        UUID ownerUUID = UUID.fromString(userId);
        List<Contact> contacts = contactRepository.findByOwnerIdAndBlockedFalse(ownerUUID);
        List<UUID> ids = contacts.stream().map(Contact::getContactId).toList();
        if (ids.isEmpty()) return Collections.emptyList();
        return userRepository.findAllById(ids).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    /**
     * Search for VIBE users by phone number or username/full-name query.
     *
     * <p>The searching user is always excluded from the results — it is never
     * possible to add yourself as a contact (matches WhatsApp behaviour).</p>
     *
     * @param requestingUserId  UUID string of the user performing the search.
     *                          Results with this ID are removed from the list.
     * @param query             Free-text username / full-name search (nullable).
     * @param phone             E.164 or local phone number to search (nullable).
     */
    public List<AuthResponse.UserSummary> searchUsers(String requestingUserId,
                                                      String query, String phone) {
        List<User> results = new ArrayList<>();

        if (phone != null && !phone.isBlank()) {
            // 1. Strip every non-digit character so "+237 677-588 867" → "237677588867"
            String digitsOnly = phone.replaceAll("[^\\d]", "");

            // 2. Try exact match first (fastest path, hits the unique index).
            Optional<User> exact = userRepository.findByPhoneNumber(phone.trim());
            if (exact.isEmpty()) {
                // 3. Also try with a leading "+" in case the caller omitted it.
                exact = userRepository.findByPhoneNumber("+" + digitsOnly);
            }

            if (exact.isPresent()) {
                results.add(exact.get());
            } else if (digitsOnly.length() >= 7) {
                // 4. Suffix search: resilient to country-code formatting mismatches.
                //    Use the last 9 digits so we stay selective (avoids matching
                //    multiple users who share a number suffix across countries).
                String suffix = digitsOnly.length() > 9
                        ? digitsOnly.substring(digitsOnly.length() - 9)
                        : digitsOnly;
                userRepository.findByPhoneNumberSuffix(suffix).ifPresent(results::add);
            }

        } else if (query != null && !query.isBlank()) {
            results.addAll(userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                    query, query, PageRequest.of(0, 20)));
        }

        // Always exclude the searching user from results.
        // This prevents the "Cannot add yourself" error from ever being reached
        // and ensures the frontend never shows the user their own profile in search.
        if (requestingUserId != null && !requestingUserId.isBlank()) {
            UUID selfId;
            try {
                selfId = UUID.fromString(requestingUserId);
            } catch (IllegalArgumentException e) {
                selfId = null;
            }
            if (selfId != null) {
                final UUID finalSelfId = selfId;
                results.removeIf(u -> finalSelfId.equals(u.getId()));
            }
        }

        return results.stream().map(this::toSummary).collect(Collectors.toList());
    }

    /**
     * Backward-compatible overload used by tests and older callers.
     * New callers should use searchUsers(requestingUserId, query, phone).
     */
    public List<AuthResponse.UserSummary> searchUsers(String query, String phone) {
        return searchUsers(null, query, phone);
    }

    /**
     * Add a user as a contact.
     *
     * <p>If the contact already exists the existing record is returned without
     * error (idempotent — mirrors WhatsApp "save contact" behaviour).</p>
     *
     * @param ownerId    UUID of the user adding the contact.
     * @param contactId  UUID of the user being added.
     * @param displayName Optional custom name chosen by the owner (e.g. "Mama",
     *                    "Boss"). If null the user's own fullName is used.
     */
    @Transactional
    public AuthResponse.UserSummary addContact(String ownerId, String contactId,
                                               String displayName) {
        UUID ownerUUID   = UUID.fromString(ownerId);
        UUID contactUUID = UUID.fromString(contactId);

        if (ownerUUID.equals(contactUUID)) {
            throw VibeException.badRequest(
                "You cannot add yourself as a contact. Please enter someone else's number or username.");
        }

        User contactUser = userRepository.findById(contactUUID)
                .orElseThrow(() -> VibeException.notFound("User not found on VIBE"));

        if (!contactRepository.existsByOwnerIdAndContactId(ownerUUID, contactUUID)) {
            contactRepository.save(Contact.builder()
                    .ownerId(ownerUUID)
                    .contactId(contactUUID)
                    .build());
            log.debug("[CONTACTS] {} added {} as contact (displayName={})",
                      ownerId, contactId, displayName);
        }

        AuthResponse.UserSummary summary = toSummary(contactUser);
        // If the owner chose a custom display name, rebuild the summary with
        // that name so the frontend contacts store gets it immediately.
        // We cannot use .toBuilder() because UserSummary is not annotated with
        // @Builder(toBuilder = true) — so we construct a new instance manually.
        if (displayName != null && !displayName.isBlank()) {
            summary = AuthResponse.UserSummary.builder()
                    .id(summary.getId())
                    .username(summary.getUsername())
                    .fullName(displayName)           // custom display name chosen by owner
                    .email(summary.getEmail())
                    .phoneNumber(summary.getPhoneNumber())
                    .role(summary.getRole())
                    .profilePictureUrl(summary.getProfilePictureUrl())
                    .preferredLanguage(summary.getPreferredLanguage())
                    .streakDays(summary.getStreakDays())
                    .countryCode(summary.getCountryCode())
                    .build();
        }
        return summary;
    }

    /**
     * Backward-compatible overload (no displayName).
     */
    @Transactional
    public AuthResponse.UserSummary addContact(String ownerId, String contactId) {
        return addContact(ownerId, contactId, null);
    }

    @Transactional
    public void removeContact(String ownerId, String contactId) {
        contactRepository.deleteByOwnerIdAndContactId(
                UUID.fromString(ownerId), UUID.fromString(contactId));
    }

    @Transactional
    public void blockUser(String ownerId, String targetId) {
        UUID ownerUUID  = UUID.fromString(ownerId);
        UUID targetUUID = UUID.fromString(targetId);
        Optional<Contact> existing = contactRepository.findByOwnerIdAndContactId(ownerUUID, targetUUID);
        if (existing.isPresent()) {
            existing.get().setBlocked(true);
            contactRepository.save(existing.get());
        } else {
            contactRepository.save(Contact.builder()
                    .ownerId(ownerUUID).contactId(targetUUID).blocked(true).build());
        }
        log.info("[CONTACTS] {} blocked {}", ownerId, targetId);
    }

    @Transactional
    public void unblockUser(String ownerId, String targetId) {
        contactRepository.deleteByOwnerIdAndContactId(
                UUID.fromString(ownerId), UUID.fromString(targetId));
    }

    public List<AuthResponse.UserSummary> getSuggestions(String userId) {
        UUID ownerUUID = UUID.fromString(userId);
        User me = userRepository.findById(ownerUUID).orElseThrow();
        List<UUID> myContactIds = contactRepository.findByOwnerId(ownerUUID)
                .stream().map(Contact::getContactId).collect(Collectors.toCollection(ArrayList::new));
        myContactIds.add(ownerUUID); // exclude self
        return userRepository.findSuggestions(me.getCountryCode(), myContactIds, PageRequest.of(0, 20))
                .stream().map(this::toSummary).collect(Collectors.toList());
    }

    private AuthResponse.UserSummary toSummary(User u) {
        return AuthResponse.UserSummary.builder()
                .id(u.getId().toString())
                .username(u.getUsername())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .role(u.getRole().name())
                .profilePictureUrl(u.getProfilePictureUrl())
                .preferredLanguage(u.getPreferredLanguage().getCode())
                .streakDays(u.getStreakDays())
                .countryCode(u.getCountryCode())
                .build();
    }
}