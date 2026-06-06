package com.vibe.authservice.controller;

import com.vibe.authservice.model.request.*;

import com.vibe.authservice.model.response.AuthResponse;
import com.vibe.authservice.service.AuthService;
import com.vibe.authservice.service.ContactService;
import com.vibe.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "VIBE auth — register, login, contacts, profile")
public class AuthController {

    private final AuthService authService;
    private final ContactService contactService;

    // ─── Auth ─────────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new VIBE user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Welcome to VIBE! Your account has been created.",
                        authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email/username and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("X-User-Id") String userId) {
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<AuthResponse.UserSummary>> getMe(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(authService.getUserById(userId)));
    }

    @PatchMapping("/me/avatar")
    @Operation(summary = "Update the current user's profile picture")
    public ResponseEntity<ApiResponse<AuthResponse.UserSummary>> updateAvatar(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.updateProfilePicture(userId, body.get("url"))));
    }

    // ─── Contacts ─────────────────────────────────────────────────────────────

    @GetMapping("/contacts")
    @Operation(summary = "Get all my contacts")
    public ResponseEntity<ApiResponse<List<AuthResponse.UserSummary>>> getContacts(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getMyContacts(userId)));
    }

    /**
     * Search VIBE users by phone number or username/name.
     *
     * <p>The searching user (identified by X-User-Id) is automatically excluded
     * from results — it is impossible to find yourself in the search results.</p>
     *
     * <p>This powers the WhatsApp-style auto-suggest in the Add Contact modal:
     * the frontend calls this as the user types and shows suggestions in real time.</p>
     *
     * @param phone  E.164 or local phone number to search for (e.g. +237677588867)
     * @param q      Free-text username or display name search
     * @param userId The requesting user's UUID (injected by API Gateway from JWT)
     */
    @GetMapping("/contacts/search")
    @Operation(summary = "Search users by phone or username — self is always excluded from results")
    public ResponseEntity<ApiResponse<List<AuthResponse.UserSummary>>> searchUsers(
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "q", required = false) String q,
            @RequestHeader(name = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                contactService.searchUsers(userId, q, phone)));
    }

    /**
     * Add a user as a contact with an optional custom display name.
     *
     * <p>Idempotent — if the contact already exists the existing record is returned
     * without error (mirrors WhatsApp "save contact" behaviour).</p>
     *
     * @param request  Contains userId (required) and optional displayName
     * @param userId   Owner's UUID from the JWT (injected by API Gateway)
     */
    @PostMapping("/contacts")
    @Operation(summary = "Add a user as contact with optional custom display name")
    public ResponseEntity<ApiResponse<AuthResponse.UserSummary>> addContact(
            @Valid @RequestBody AddContactRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        contactService.addContact(userId, request.getUserId(),
                                request.getDisplayName())));
    }

    @DeleteMapping("/contacts/{contactId}")
    @Operation(summary = "Remove a contact")
    public ResponseEntity<ApiResponse<Void>> removeContact(
            @PathVariable("contactId") String contactId,
            @RequestHeader("X-User-Id") String userId) {
        contactService.removeContact(userId, contactId);
        return ResponseEntity.ok(ApiResponse.success("Contact removed", null));
    }

    @PostMapping("/contacts/{targetId}/block")
    @Operation(summary = "Block a user")
    public ResponseEntity<ApiResponse<Void>> blockUser(
            @PathVariable("targetId") String targetId,
            @RequestHeader("X-User-Id") String userId) {
        contactService.blockUser(userId, targetId);
        return ResponseEntity.ok(ApiResponse.success("User blocked", null));
    }

    @DeleteMapping("/contacts/{targetId}/block")
    @Operation(summary = "Unblock a user")
    public ResponseEntity<ApiResponse<Void>> unblockUser(
            @PathVariable("targetId") String targetId,
            @RequestHeader("X-User-Id") String userId) {
        contactService.unblockUser(userId, targetId);
        return ResponseEntity.ok(ApiResponse.success("User unblocked", null));
    }

    @GetMapping("/contacts/suggestions")
    @Operation(summary = "Get friend suggestions (Meet Friends)")
    public ResponseEntity<ApiResponse<List<AuthResponse.UserSummary>>> getSuggestions(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(contactService.getSuggestions(userId)));
    }

    // ─── Forgot / Reset Password ───────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Send 6-digit OTP to email for password reset (logged to console in dev)")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("If that email exists, an OTP has been sent.", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the OTP sent to email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. Please login.", null));
    }

    // ─── Phone OTP Verification ────────────────────────────────────────────────

    @PostMapping("/send-phone-otp")
    @Operation(summary = "Send 6-digit OTP to phone number (logged to console in dev)")
    public ResponseEntity<ApiResponse<Void>> sendPhoneOtp(
            @Valid @RequestBody SendPhoneOtpRequest request) {
        authService.sendPhoneOtp(request.getPhoneNumber());
        return ResponseEntity.ok(ApiResponse.success("OTP sent to " + request.getPhoneNumber(), null));
    }

    @PostMapping("/verify-phone")
    @Operation(summary = "Verify phone number using OTP")
    public ResponseEntity<ApiResponse<Void>> verifyPhone(
            @Valid @RequestBody VerifyPhoneRequest request) {
        authService.verifyPhone(request.getPhoneNumber(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("Phone verified successfully!", null));
    }
}