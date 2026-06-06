package com.vibe.authservice.model.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SendPhoneOtpRequest {
    @NotBlank
    @Pattern(regexp="^\\+?[1-9]\\d{1,14}$", message="Invalid phone number")
    private String phoneNumber;
}
