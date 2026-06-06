package com.vibe.authservice.service;

import com.vibe.authservice.kafka.VibeEventPublisher;
import com.vibe.authservice.model.entity.User;
import com.vibe.authservice.model.request.*;
import com.vibe.authservice.model.response.AuthResponse;
import com.vibe.authservice.repository.UserRepository;
import com.vibe.common.enums.Language;
import com.vibe.common.enums.UserRole;
import com.vibe.common.exception.VibeException;
import com.vibe.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}. All collaborators (repository, password
 * encoder, JWT util, token cache, event publisher) are mocked, so these tests
 * exercise pure business logic with no database, Redis or Kafka.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock TokenCacheService tokenCache;
    @Mock VibeEventPublisher eventPublisher;

    @InjectMocks AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Alice Test")
                .username("alice")
                .email("alice@vibe.cm")
                .passwordHash("hashed")
                .phoneNumber("+237677111222")
                .role(UserRole.USER)
                .preferredLanguage(Language.ENGLISH)
                .isActive(true)
                .build();

        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    }

    private RegisterRequest registerRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setFullName("Alice Test");
        r.setUsername("Alice");                 // mixed case — service must lowercase it
        r.setEmail("Alice@vibe.cm");
        r.setPassword("Passw0rd!1");
        r.setPhoneNumber("+237 677 111 222");   // spaces — service must sanitise
        r.setPreferredLanguage("en");
        r.setCountryCode("CM");
        return r;
    }

    // ─── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success_returnsTokens_publishesEvent_cachesRefreshToken() {
        when(userRepository.existsByEmail("alice@vibe.cm")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse res = authService.register(registerRequest());

        assertThat(res.getAccessToken()).isEqualTo("access-token");
        assertThat(res.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(res.getUser().getUsername()).isEqualTo("alice");
        verify(eventPublisher).publishUserRegistered(any(User.class));
        verify(tokenCache).set(contains("refresh"), eq("refresh-token"), any(Duration.class));
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("alice@vibe.cm")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test
    void register_invalidPhone_throwsBadRequest() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        RegisterRequest r = registerRequest();
        r.setPhoneNumber("123"); // far too short

        assertThatThrownBy(() -> authService.register(r))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("Phone number");
    }

    // ─── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_updatesLastLogin() {
        when(userRepository.findByEmail("alice@vibe.cm")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!1", "hashed")).thenReturn(true);

        AuthResponse res = authService.login(loginRequest("alice@vibe.cm", "Passw0rd!1"));

        assertThat(res.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).updateLastLogin(eq(user.getId()), any(Instant.class));
    }

    @Test
    void login_byUsername_whenIdentifierHasNoAtSymbol() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        authService.login(loginRequest("alice", "x"));

        verify(userRepository).findByUsername("alice");
    }

    @Test
    void login_wrongPassword_incrementsFailedAttemptsAndThrows() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("alice@vibe.cm", "wrong")))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("Invalid credentials");
        verify(userRepository).incrementFailedAttempts(user.getId());
    }

    @Test
    void login_userNotFound_throwsUnauthorized() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("ghost@vibe.cm", "x")))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void login_suspendedAccount_throws() {
        user.setActive(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest("alice@vibe.cm", "x")))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void login_lockedAccount_throws() {
        user.setLockedUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest("alice@vibe.cm", "x")))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("locked");
    }

    // ─── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refreshToken_success() {
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.extractUserId("rt")).thenReturn(user.getId().toString());
        when(tokenCache.get(anyString())).thenReturn(null);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("rt");

        assertThat(authService.refreshToken(req).getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void refreshToken_invalid_throws() {
        when(jwtUtil.validateToken("bad")).thenReturn(false);
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("bad");

        assertThatThrownBy(() -> authService.refreshToken(req)).isInstanceOf(VibeException.class);
    }

    @Test
    void refreshToken_revoked_throws() {
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.extractUserId("rt")).thenReturn(user.getId().toString());
        when(tokenCache.get(anyString())).thenReturn("a-different-stored-token");

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("rt");

        assertThatThrownBy(() -> authService.refreshToken(req))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("revoked");
    }

    // ─── logout / profile ──────────────────────────────────────────────────────

    @Test
    void logout_deletesRefreshToken() {
        authService.logout("user-9");
        verify(tokenCache).delete(contains("user-9"));
    }

    @Test
    void getUserById_success() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        assertThat(authService.getUserById(user.getId().toString()).getUsername()).isEqualTo("alice");
    }

    @Test
    void getUserById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.getUserById(id.toString()))
                .isInstanceOf(VibeException.class);
    }

    @Test
    void updateProfilePicture_savesUrlAndReturnsSummary() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse.UserSummary s =
                authService.updateProfilePicture(user.getId().toString(), "http://img/p.png");

        assertThat(user.getProfilePictureUrl()).isEqualTo("http://img/p.png");
        assertThat(s.getUsername()).isEqualTo("alice");
        verify(userRepository).save(user);
    }

    // ─── forgot / reset password ───────────────────────────────────────────────

    @Test
    void forgotPassword_userExists_storesOtp() {
        when(userRepository.findByEmail("alice@vibe.cm")).thenReturn(Optional.of(user));
        authService.forgotPassword("Alice@vibe.cm");
        verify(tokenCache).set(contains("otp:reset"), anyString(), any(Duration.class));
    }

    @Test
    void forgotPassword_userMissing_doesNothing() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        authService.forgotPassword("ghost@vibe.cm");
        verify(tokenCache, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void resetPassword_validOtp_updatesPasswordAndClearsOtp() {
        when(tokenCache.get(contains("otp:reset"))).thenReturn("123456");
        when(userRepository.findByEmail("alice@vibe.cm")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("new-hash");

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("alice@vibe.cm");
        req.setOtp("123456");
        req.setNewPassword("NewPass1!");

        authService.resetPassword(req);

        verify(userRepository).save(user);
        verify(tokenCache).delete(contains("otp:reset"));
    }

    @Test
    void resetPassword_invalidOtp_throws() {
        when(tokenCache.get(anyString())).thenReturn("999999");
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("alice@vibe.cm");
        req.setOtp("000000");
        req.setNewPassword("NewPass1!");

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(VibeException.class)
                .hasMessageContaining("OTP");
    }

    // ─── phone OTP ─────────────────────────────────────────────────────────────

    @Test
    void sendPhoneOtp_storesOtp() {
        authService.sendPhoneOtp("+237677111222");
        verify(tokenCache).set(contains("otp:phone"), anyString(), any(Duration.class));
    }

    @Test
    void verifyPhone_validOtp_marksVerified() {
        when(tokenCache.get(contains("otp:phone"))).thenReturn("123456");
        when(userRepository.findByPhoneNumber("+237677111222")).thenReturn(Optional.of(user));

        authService.verifyPhone("+237677111222", "123456");

        verify(userRepository).save(user);
        verify(tokenCache).delete(contains("otp:phone"));
    }

    @Test
    void verifyPhone_invalidOtp_throws() {
        when(tokenCache.get(anyString())).thenReturn("123456");
        assertThatThrownBy(() -> authService.verifyPhone("+237677111222", "000000"))
                .isInstanceOf(VibeException.class);
    }

    // ─── helper ────────────────────────────────────────────────────────────────

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
