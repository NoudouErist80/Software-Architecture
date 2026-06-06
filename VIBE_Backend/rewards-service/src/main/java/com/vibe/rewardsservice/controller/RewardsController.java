package com.vibe.rewardsservice.controller;

import com.vibe.common.response.ApiResponse;
import com.vibe.rewardsservice.model.request.CashoutRequestDto;
import com.vibe.rewardsservice.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rewards")
@RequiredArgsConstructor
@Tag(name = "Rewards & Token Wallet", description = "VIBE token economy — earn, cashout, leaderboard")
public class RewardsController {

    private final RewardsService rewardsService;
    private final LeaderboardService leaderboardService;

    @GetMapping("/wallet")
    @Operation(summary = "Get current user token wallet")
    public ResponseEntity<ApiResponse<Object>> getWallet(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(rewardsService.getWallet(userId)));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get token transaction history")
    public ResponseEntity<ApiResponse<Object>> getTransactions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                rewardsService.getTransactionHistory(userId, page, size)));
    }

    @PostMapping("/cashout")
    @Operation(summary = "Request cashout to MTN MoMo or Orange Money (2-step: request → confirm)")
    public ResponseEntity<ApiResponse<Object>> requestCashout(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CashoutRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success("Cashout request submitted",
                rewardsService.requestCashout(userId, request)));
    }

    @GetMapping("/rates")
    @Operation(summary = "Get current token conversion rates and cashout info")
    public ResponseEntity<ApiResponse<Object>> getRates() {
        return ResponseEntity.ok(ApiResponse.success(rewardsService.getRates()));
    }

    @PostMapping("/streak/claim")
    @Operation(summary = "Claim daily streak bonus (auto-credited on login)")
    public ResponseEntity<ApiResponse<Object>> claimStreak(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(rewardsService.claimStreakBonus(userId)));
    }

    @GetMapping("/leaderboard/weekly")
    @Operation(summary = "Get weekly top token earners")
    public ResponseEntity<ApiResponse<Object>> getWeeklyLeaderboard() {
        return ResponseEntity.ok(ApiResponse.success(leaderboardService.getWeeklyLeaderboard()));
    }
}
