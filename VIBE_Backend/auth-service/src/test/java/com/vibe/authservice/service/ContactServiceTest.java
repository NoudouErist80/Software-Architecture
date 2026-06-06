package com.vibe.authservice.service;

import com.vibe.authservice.model.entity.Contact;
import com.vibe.authservice.model.entity.User;
import com.vibe.authservice.model.response.AuthResponse;
import com.vibe.authservice.repository.ContactRepository;
import com.vibe.authservice.repository.UserRepository;
import com.vibe.common.enums.Language;
import com.vibe.common.enums.UserRole;
import com.vibe.common.exception.VibeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ContactService} — contact list, search (phone/username),
 * add/remove, block/unblock, and friend suggestions. Repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactServiceTest {

    @Mock ContactRepository contactRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ContactService contactService;

    private User user;
    private UUID ownerId;
    private UUID contactId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        user = User.builder()
                .id(contactId).fullName("Bob").username("bob").email("bob@vibe.cm")
                .phoneNumber("+237677000111").role(UserRole.USER)
                .preferredLanguage(Language.ENGLISH).build();
    }

    @Test
    void getMyContacts_returnsSummaries() {
        Contact c = Contact.builder().ownerId(ownerId).contactId(contactId).build();
        when(contactRepository.findByOwnerIdAndBlockedFalse(ownerId)).thenReturn(List.of(c));
        when(userRepository.findAllById(List.of(contactId))).thenReturn(List.of(user));

        List<AuthResponse.UserSummary> res = contactService.getMyContacts(ownerId.toString());

        assertThat(res).hasSize(1);
        assertThat(res.get(0).getUsername()).isEqualTo("bob");
    }

    @Test
    void getMyContacts_noContacts_returnsEmptyList() {
        when(contactRepository.findByOwnerIdAndBlockedFalse(ownerId)).thenReturn(List.of());
        assertThat(contactService.getMyContacts(ownerId.toString())).isEmpty();
    }

    @Test
    void searchUsers_byExactPhone() {
        when(userRepository.findByPhoneNumber("+237677000111")).thenReturn(Optional.of(user));
        assertThat(contactService.searchUsers(null, null, "+237677000111")).hasSize(1);
    }

    @Test
    void searchUsers_byPhoneSuffix_whenNoExactMatch() {
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumberSuffix(anyString())).thenReturn(Optional.of(user));
        assertThat(contactService.searchUsers(null, null, "677000111")).hasSize(1);
    }

    @Test
    void searchUsers_byUsernameQuery() {
        when(userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                eq("bob"), eq("bob"), any(Pageable.class))).thenReturn(List.of(user));
        assertThat(contactService.searchUsers("bob", null)).hasSize(1);
    }

    @Test
    void searchUsers_excludesTheRequestingUser() {
        when(userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                anyString(), anyString(), any(Pageable.class))).thenReturn(List.of(user));
        // requesting user IS the only result → filtered out
        assertThat(contactService.searchUsers(contactId.toString(), "bob", null)).isEmpty();
    }

    @Test
    void addContact_newContact_savesAndReturnsSummary() {
        when(userRepository.findById(contactId)).thenReturn(Optional.of(user));
        when(contactRepository.existsByOwnerIdAndContactId(ownerId, contactId)).thenReturn(false);

        AuthResponse.UserSummary res = contactService.addContact(ownerId.toString(), contactId.toString(), null);

        assertThat(res.getUsername()).isEqualTo("bob");
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    void addContact_withDisplayName_overridesFullName_andIsIdempotent() {
        when(userRepository.findById(contactId)).thenReturn(Optional.of(user));
        when(contactRepository.existsByOwnerIdAndContactId(any(), any())).thenReturn(true); // already saved

        AuthResponse.UserSummary res = contactService.addContact(ownerId.toString(), contactId.toString(), "Boss");

        assertThat(res.getFullName()).isEqualTo("Boss");
        verify(contactRepository, never()).save(any());
    }

    @Test
    void addContact_self_throwsBadRequest() {
        assertThatThrownBy(() -> contactService.addContact(ownerId.toString(), ownerId.toString(), null))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("cannot add yourself");
    }

    @Test
    void addContact_userNotFound_throws() {
        when(userRepository.findById(contactId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> contactService.addContact(ownerId.toString(), contactId.toString(), null))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void removeContact_delegatesToRepository() {
        contactService.removeContact(ownerId.toString(), contactId.toString());
        verify(contactRepository).deleteByOwnerIdAndContactId(ownerId, contactId);
    }

    @Test
    void blockUser_existingContact_setsBlockedTrue() {
        Contact c = Contact.builder().ownerId(ownerId).contactId(contactId).build();
        when(contactRepository.findByOwnerIdAndContactId(ownerId, contactId)).thenReturn(Optional.of(c));

        contactService.blockUser(ownerId.toString(), contactId.toString());

        assertThat(c.isBlocked()).isTrue();
        verify(contactRepository).save(c);
    }

    @Test
    void blockUser_noExistingContact_createsBlockedRecord() {
        when(contactRepository.findByOwnerIdAndContactId(any(), any())).thenReturn(Optional.empty());
        contactService.blockUser(ownerId.toString(), contactId.toString());
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    void unblockUser_delegatesToRepository() {
        contactService.unblockUser(ownerId.toString(), contactId.toString());
        verify(contactRepository).deleteByOwnerIdAndContactId(ownerId, contactId);
    }

    @Test
    void getSuggestions_returnsSummaries() {
        User me = User.builder().id(ownerId).countryCode("CM").username("me")
                .fullName("Me").email("me@vibe.cm").role(UserRole.USER)
                .preferredLanguage(Language.ENGLISH).build();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(me));
        when(contactRepository.findByOwnerId(ownerId)).thenReturn(List.of());
        when(userRepository.findSuggestions(eq("CM"), anyList(), any(Pageable.class)))
                .thenReturn(List.of(user));

        assertThat(contactService.getSuggestions(ownerId.toString())).hasSize(1);
    }
}
