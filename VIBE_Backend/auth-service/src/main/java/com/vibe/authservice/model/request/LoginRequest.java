package com.vibe.authservice.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    /**
     * Accepts "email", "username", or "identifier" from the frontend.
     * Supports both email and username login.
     */
    @NotBlank(message = "email or username is required")
    @JsonAlias({"Email", "identifier"})
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
