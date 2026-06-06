package com.vibe.rewardsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String WEEKLY_LEADERBOARD_KEY = "vibe:leaderboard:weekly";

    public List<Map<String, Object>> getWeeklyLeaderboard() {
        Set<ZSetOperations.TypedTuple<Object>> entries =
                redisTemplate.opsForZSet().reverseRangeWithScores(WEEKLY_LEADERBOARD_KEY, 0, 49);
        if (entries == null) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> entry : entries) {
            result.add(Map.of(
                    "rank", rank++,
                    "userId", entry.getValue(),
                    "tokens", entry.getScore() != null ? entry.getScore().longValue() : 0L
            ));
        }
        return result;
    }
}
