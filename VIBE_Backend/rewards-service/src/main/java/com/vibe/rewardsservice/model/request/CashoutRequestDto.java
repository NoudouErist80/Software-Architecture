package com.vibe.rewardsservice.model.request;

import com.vibe.rewardsservice.model.entity.enums.CashoutProvider;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CashoutRequestDto {
    @NotNull
    @Min(500)
    private int tokenAmount;
    @NotNull
    private CashoutProvider provider; // MTN_MOMO, ORANGE_MONEY
    @NotBlank
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number")
    private String phoneNumber;
}
