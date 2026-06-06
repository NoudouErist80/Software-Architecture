package com.vibe.authservice.model.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min=6, max=6)
    private String otp;
    @NotBlank @Size(min=8, max=100)
    @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message="Password must contain uppercase, lowercase, and a digit")
    private String newPassword;
}
