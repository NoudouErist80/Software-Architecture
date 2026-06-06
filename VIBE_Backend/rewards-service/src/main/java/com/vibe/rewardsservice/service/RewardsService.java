package com.vibe.rewardsservice.service;

import com.vibe.common.constants.VibeConstants;
import com.vibe.common.enums.TokenEarnType;
import com.vibe.common.exception.VibeException;
import com.vibe.rewardsservice.model.entity.*;
import com.vibe.rewardsservice.model.entity.enums.*;
import com.vibe.rewardsservice.model.request.CashoutRequestDto;
import com.vibe.rewardsservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardsService {

    private final TokenWalletRepository walletRepository;
    private final TokenTransactionRepository transactionRepository;
    private final CashoutRepository cashoutRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void creditTokens(String userId, int amount, TokenEarnType earnType, String referenceId) {
        UUID userUUID = UUID.fromString(userId);

        String dailyKey = VibeConstants.CACHE_DAILY_EARNINGS + userId;
        Object raw = redisTemplate.opsForValue().get(dailyKey);
        int todayEarned = raw != null ? ((Number) raw).intValue() : 0;

        if (todayEarned >= VibeConstants.TOKEN_DAILY_EARNING_CAP) {
            log.debug("Daily cap reached for {}", userId);
            return;
        }

        int actualAmount = Math.min(amount, VibeConstants.TOKEN_DAILY_EARNING_CAP - todayEarned);
        if (actualAmount <= 0) return;

        TokenWallet wallet = walletRepository.findByUserIdForUpdate(userUUID)
                .orElseGet(() -> walletRepository.save(
                        TokenWallet.builder().userId(userUUID).balance(0L).build()));

        wallet.setBalance(wallet.getBalance() + actualAmount);
        wallet.setTotalEarned(wallet.getTotalEarned() + actualAmount);
        walletRepository.save(wallet);

        transactionRepository.save(TokenTransaction.builder()
                .userId(userUUID)
                .amount(actualAmount)
                .type(TransactionType.CREDIT)
                .earnType(earnType)
                .referenceId(referenceId)
                .description(earnType.getDescription())
                .balanceAfter(wallet.getBalance())
                .createdAt(Instant.now())
                .build());

        // Update daily earning counter in Redis (expires at midnight)
        redisTemplate.opsForValue().set(dailyKey, todayEarned + actualAmount,
                Duration.ofSeconds(secondsUntilMidnight()));

        // Update leaderboard score in Redis sorted set
        redisTemplate.opsForZSet().incrementScore("vibe:leaderboard:weekly", userId, actualAmount);

        log.info("Credited {} tokens to {} for {}", actualAmount, userId, earnType);
    }

    public TokenWallet getWallet(String userId) {
        return walletRepository.findByUserId(UUID.fromString(userId))
                .orElseGet(() -> walletRepository.save(
                        TokenWallet.builder().userId(UUID.fromString(userId)).balance(0L).build()));
    }

    public Page<TokenTransaction> getTransactionHistory(String userId, int page, int size) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(
                UUID.fromString(userId), PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional
    public CashoutRequest requestCashout(String userId, CashoutRequestDto dto) {
        UUID userUUID = UUID.fromString(userId);
        TokenWallet wallet = walletRepository.findByUserIdForUpdate(userUUID)
                .orElseThrow(() -> VibeException.notFound("Wallet"));

        if (wallet.getBalance() < VibeConstants.TOKEN_MIN_CASHOUT)
            throw VibeException.badRequest("Minimum cashout is " + VibeConstants.TOKEN_MIN_CASHOUT + " tokens");
        if (dto.getTokenAmount() > wallet.getBalance())
            throw VibeException.badRequest("Insufficient token balance");
        if (dto.getTokenAmount() < VibeConstants.TOKEN_MIN_CASHOUT)
            throw VibeException.badRequest("Minimum cashout is " + VibeConstants.TOKEN_MIN_CASHOUT + " tokens");

        BigDecimal xafAmount = BigDecimal.valueOf(dto.getTokenAmount())
                .multiply(BigDecimal.valueOf(VibeConstants.TOKEN_TO_XAF_RATE));

        // Debit wallet immediately (lock funds during processing)
        wallet.setBalance(wallet.getBalance() - dto.getTokenAmount());
        walletRepository.save(wallet);

        transactionRepository.save(TokenTransaction.builder()
                .userId(userUUID)
                .amount(-dto.getTokenAmount())
                .type(TransactionType.DEBIT)
                .earnType(TokenEarnType.CASHOUT)
                .description("Cashout to " + dto.getProvider().name())
                .balanceAfter(wallet.getBalance())
                .createdAt(Instant.now())
                .build());

        return cashoutRepository.save(CashoutRequest.builder()
                .userId(userUUID)
                .tokenAmount(dto.getTokenAmount())
                .xafAmount(xafAmount)
                .provider(dto.getProvider())
                .phoneNumber(dto.getPhoneNumber())
                .status(CashoutStatus.PENDING)
                .createdAt(Instant.now())
                .build());
    }

    public Map<String, Object> getRates() {
        return Map.of(
                "tokensToXaf", VibeConstants.TOKEN_TO_XAF_RATE,
                "minCashout", VibeConstants.TOKEN_MIN_CASHOUT,
                "dailyCap", VibeConstants.TOKEN_DAILY_EARNING_CAP,
                "providers", List.of("MTN_MOMO", "ORANGE_MONEY"),
                "supportedCountries", List.of("CM", "NG", "GH", "SN", "CI", "CD")
        );
    }

    public Map<String, Object> claimStreakBonus(String userId) {
        // Called when user explicitly claims their streak bonus
        return Map.of("message", "Streak bonus credited automatically on login");
    }

    private long secondsUntilMidnight() {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        return Duration.between(now, LocalTime.MIDNIGHT).getSeconds() + 86400;
    }
}
