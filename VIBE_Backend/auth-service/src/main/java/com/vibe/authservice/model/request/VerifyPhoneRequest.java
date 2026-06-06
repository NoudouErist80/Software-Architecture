package com.vibe.authservice.model.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VerifyPhoneRequest {
    @NotBlank
    private String phoneNumber;
    @NotBlank @Size(min=6, max=6)
    private String otp;
}
