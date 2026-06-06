package com.vibe.authservice.service;

import com.vibe.authservice.kafka.VibeEventPublisher;
import com.vibe.authservice.model.entity.User;
import com.vibe.authservice.model.request.*;
import com.vibe.authservice.model.response.AuthResponse;
import com.vibe.authservice.repository.UserRepository;
import com.vibe.common.constants.VibeConstants;
import com.vibe.common.enums.Language;
import com.vibe.common.exception.VibeException;
import com.vibe.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Core authentication service.
 *
 * Uses {@link TokenCacheService} for all token/OTP storage, which provides
 * transparent Redis → in-memory fallback so that registration and login work
 * even when Redis is not running in local-dev mode.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository     userRepository;
    private final PasswordEncoder    passwordEncoder;
    private final JwtUtil            jwtUtil;
    private final TokenCacheService  tokenCache;        // Redis-optional cache
    private final VibeEventPublisher eventPublisher;

    // ─── Register ─────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("🔐 Register: fullName='{}', username='{}', email='{}', phone='{}', language='{}'",
                request.getFullName(), request.getUsername(), request.getEmail(),
                request.getPhoneNumber(), request.getPreferredLanguage());

        if (userRepository.existsByEmail(request.getEmail().toLowerCase()))
            throw VibeException.conflict("Email already registered on VIBE");
        if (userRepository.existsByUsername(request.getUsername().toLowerCase()))
            throw VibeException.conflict("Username already taken");

        String cleanedPhone = sanitisePhone(request.getPhoneNumber());
        Language language   = Language.fromCode(request.getPreferredLanguage());

        User user = User.builder()
                .fullName(request.getFullName())
                .username(request.getUsername().toLowerCase())
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(cleanedPhone)
                .countryCode(request.getCountryCode() != null ? request.getCountryCode() : "CM")
                .preferredLanguage(language)
                .build();

        user = userRepository.save(user);
        log.info("✅ New user saved: {} ({})", user.getUsername(), user.getId());

        eventPublisher.publishUserRegistered(user);   // async + circuit-breaker protected

        return buildAuthResponse(user);
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = findUserByIdentifier(request.getEmail());

        if (!user.isActive())
            throw VibeException.unauthorized("Account suspended. Contact VIBE support.");
        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil()))
            throw VibeException.unauthorized("Account temporarily locked. Try again later.");
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            userRepository.incrementFailedAttempts(user.getId());
            throw VibeException.unauthorized("Invalid credentials");
        }

        userRepository.updateLastLogin(user.getId(), Instant.now());
        updateStreakIfNeeded(user);

        log.info("🔓 Login successful: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    // ─── Refresh Token ────────────────────────────────────────────────────────

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (!jwtUtil.validateToken(token))
            throw VibeException.unauthorized("Invalid or expired refresh token");

        String userId = jwtUtil.extractUserId(token);
        String stored = tokenCache.get(VibeConstants.CACHE_REFRESH_TOKEN + userId);

        if (stored != null && !token.equals(stored))
            throw VibeException.unauthorized("Refresh token revoked");

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> VibeException.notFound("User"));
        return buildAuthResponse(user);
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    public void logout(String userId) {
        tokenCache.delete(VibeConstants.CACHE_REFRESH_TOKEN + userId);
        log.info("👋 Logout: {}", userId);
    }

    // ─── Profile ──────────────────────────────────────────────────────────────

    public AuthResponse.UserSummary getUserById(String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> VibeException.notFound("User"));
        return toUserSummary(user);
    }

    /** Update the current user's profile picture URL and return the fresh profile. */
    @Transactional
    public AuthResponse.UserSummary updateProfilePicture(String userId, String url) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> VibeException.notFound("User"));
        user.setProfilePictureUrl(url);
        userRepository.save(user);
        log.info("🖼️  Profile picture updated for {}", user.getUsername());
        return toUserSummary(user);
    }

    // ─── Forgot / Reset Password ──────────────────────────────────────────────

    public void forgotPassword(String email) {
        userRepository.findByEmail(email.toLowerCase()).ifPresent(user -> {
            String otp = String.format("%06d", new java.util.Random().nextInt(999999));
            tokenCache.set("vibe:otp:reset:" + email.toLowerCase(), otp, Duration.ofMinutes(10));
            log.info("🔑 PASSWORD RESET OTP for {} : {}", email, otp);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String key    = "vibe:otp:reset:" + request.getEmail().toLowerCase();
        String stored = tokenCache.get(key);
        if (stored == null || !stored.equals(request.getOtp()))
            throw VibeException.badRequest("Invalid or expired OTP");

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> VibeException.notFound("User"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        tokenCache.delete(key);
        log.info("🔒 Password reset for {}", request.getEmail());
    }

    // ─── Phone OTP ────────────────────────────────────────────────────────────

    public void sendPhoneOtp(String phoneNumber) {
        String otp = String.format("%06d", new java.util.Random().nextInt(999999));
        tokenCache.set("vibe:otp:phone:" + phoneNumber, otp, Duration.ofMinutes(10));
        log.info("📱 PHONE OTP for {} : {}", phoneNumber, otp);
    }

    @Transactional
    public void verifyPhone(String phoneNumber, String otp) {
        String key    = "vibe:otp:phone:" + phoneNumber;
        String stored = tokenCache.get(key);
        if (stored == null || !stored.equals(otp))
            throw VibeException.badRequest("Invalid or expired phone OTP");

        userRepository.findByPhoneNumber(phoneNumber).ifPresent(user -> {
            user.setPhoneVerified(true);
            userRepository.save(user);
        });
        tokenCache.delete(key);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private User findUserByIdentifier(String identifier) {
        return identifier.contains("@")
                ? userRepository.findByEmail(identifier.toLowerCase())
                        .orElseThrow(() -> VibeException.unauthorized("Invalid credentials"))
                : userRepository.findByUsername(identifier.toLowerCase())
                        .orElseThrow(() -> VibeException.unauthorized("Invalid credentials"));
    }

    /**
     * Generates JWT pair and stores refresh token in cache (Redis or in-memory).
     * Never throws due to cache failures — auth response is always returned.
     */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtUtil.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

        tokenCache.set(
                VibeConstants.CACHE_REFRESH_TOKEN + user.getId(),
                refreshToken,
                Duration.ofDays(7));

        log.debug("🎟️  Tokens issued for '{}' ({})", user.getUsername(), user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accessTokenExpiresIn(900)
                .user(toUserSummary(user))
                .build();
    }

    private void updateStreakIfNeeded(User user) {
        Instant now       = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        if (user.getLastStreakDate() == null ||
                user.getLastStreakDate().isBefore(yesterday.truncatedTo(ChronoUnit.DAYS))) {
            int newStreak = (user.getLastStreakDate() != null &&
                             user.getLastStreakDate().isAfter(yesterday))
                    ? user.getStreakDays() + 1 : 1;
            userRepository.updateStreak(user.getId(), newStreak, now);
            eventPublisher.publishStreakEarned(user, newStreak);
        }
    }

    private String sanitisePhone(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String cleaned = raw.replaceAll("\\s+", "").replaceAll("[()\\-]", "").trim();
        if (!cleaned.startsWith("+")) cleaned = "+" + cleaned;
        String digitsOnly = cleaned.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 7 || digitsOnly.length() > 15) {
            log.warn("⚠️ Invalid phone number: {} digits in '{}'", digitsOnly.length(), raw);
            throw VibeException.badRequest("Phone number must contain 7–15 digits");
        }
        return cleaned;
    }

    public AuthResponse.UserSummary toUserSummary(User user) {
        return AuthResponse.UserSummary.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .profilePictureUrl(user.getProfilePictureUrl())
                .preferredLanguage(user.getPreferredLanguage().getCode())
                .streakDays(user.getStreakDays())
                .countryCode(user.getCountryCode())
                .build();
    }
}