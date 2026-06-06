package com.vibe.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibe.authservice.model.request.LoginRequest;
import com.vibe.authservice.model.request.RegisterRequest;
import com.vibe.authservice.model.response.AuthResponse;
import com.vibe.authservice.service.AuthService;
import com.vibe.authservice.service.ContactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration (web-slice) tests for {@link AuthController}. Loads the real Spring
 * MVC stack — routing, JSON (de)serialisation, validation — with the service
 * layer mocked. Security filters are disabled so we test the controller itself.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean ContactService contactService;

    @Test
    void register_returns201WithTokens() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .accessToken("access").refreshToken("refresh").tokenType("Bearer").build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(resp);

        RegisterRequest req = new RegisterRequest();
        req.setFullName("Alice Test");
        req.setUsername("alice");
        req.setEmail("alice@vibe.cm");
        req.setPassword("Passw0rd!1");
        req.setPhoneNumber("+237677111222");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())   // controller returns 201 CREATED
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void register_invalidBody_returns400() throws Exception {
        // blank required fields → bean validation should reject before the service is hit
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithTokens() throws Exception {
        AuthResponse resp = AuthResponse.builder().accessToken("access").refreshToken("refresh").build();
        when(authService.login(any(LoginRequest.class))).thenReturn(resp);

        LoginRequest req = new LoginRequest();
        req.setEmail("alice@vibe.cm");
        req.setPassword("Passw0rd!1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    void getMe_returnsCurrentUser() throws Exception {
        AuthResponse.UserSummary summary =
                AuthResponse.UserSummary.builder().username("alice").role("USER").build();
        when(authService.getUserById("user-1")).thenReturn(summary);

        mockMvc.perform(get("/api/v1/auth/me").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void updateAvatar_returnsUpdatedProfile() throws Exception {
        AuthResponse.UserSummary summary = AuthResponse.UserSummary.builder()
                .username("alice").profilePictureUrl("http://img/p.png").build();
        when(authService.updateProfilePicture(eq("user-1"), any())).thenReturn(summary);

        mockMvc.perform(patch("/api/v1/auth/me/avatar")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://img/p.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profilePictureUrl").value("http://img/p.png"));
    }
}
