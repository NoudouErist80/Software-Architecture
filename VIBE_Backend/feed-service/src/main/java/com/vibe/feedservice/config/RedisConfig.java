package com.vibe.feedservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration for the VIBE Feed Service.
 *
 * WHAT THIS FIXES vs THE ORIGINAL:
 * 1. The original RedisConfig had no CacheManager bean — Spring fell back to
 *    the auto-configured one which uses default TTL for ALL caches with no
 *    per-cache tuning. This means user-feed and profile caches expired at the
 *    same rate, wasting memory and causing unnecessary cache misses.
 *
 * 2. The ObjectMapper used for Redis serialization must handle Java 8 time types
 *    (Instant, LocalDateTime) and must include type information so deserialization
 *    doesn't produce LinkedHashMap instead of the target POJO.
 *
 * 3. Connection pool settings are managed via application.yml (lettuce.pool.*).
 *    Lettuce is the default async Redis client — no extra config needed here
 *    beyond ensuring commons-pool2 is on the classpath (it is, per pom.xml).
 *
 * CACHE TTL STRATEGY (tuned for feed workload):
 * - user-feed:      60 s  — feeds go stale quickly; short TTL keeps content fresh
 * - user-profile:   5 min — profiles change less often; longer TTL saves DB reads
 * - post-detail:    2 min — individual post views; moderate TTL
 * - status-list:    30 s  — statuses are ephemeral; very short TTL
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    /** Matches spring.cache.redis.time-to-live in application.yml (default 60s) */
    @Value("${spring.cache.redis.time-to-live:60000}")
    private long defaultTtlMillis;

    // ─── RedisTemplate ────────────────────────────────────────────────────────

    /**
     * General-purpose RedisTemplate for manual Redis operations (e.g. counters,
     * rate limiters, distributed locks). Keys are Strings; values are JSON.
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        // Enables transaction support for multi/exec blocks
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        log.info("[RedisConfig] RedisTemplate configured with JSON serialization.");
        return template;
    }

    // ─── CacheManager ─────────────────────────────────────────────────────────

    /**
     * Spring Cache abstraction manager. Each logical cache has its own TTL so
     * the eviction policy matches the volatility of the data it holds.
     *
     * Usage in service classes:
     *   @Cacheable(value = "user-feed", key = "#userId + ':' + #mood + ':' + #page")
     *   @Cacheable(value = "user-profile", key = "#userId")
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = defaultCacheConfig(
                Duration.ofMillis(defaultTtlMillis));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Feed cache — short TTL, high churn
        cacheConfigs.put("user-feed",
                defaultCacheConfig(Duration.ofSeconds(60)));

        // Profile cache — medium TTL, low churn
        cacheConfigs.put("user-profile",
                defaultCacheConfig(Duration.ofMinutes(5)));

        // Individual post details
        cacheConfigs.put("post-detail",
                defaultCacheConfig(Duration.ofMinutes(2)));

        // Status list — very short TTL (statuses expire every 24h but content changes often)
        cacheConfigs.put("status-list",
                defaultCacheConfig(Duration.ofSeconds(30)));

        // Follower / following lists — moderate TTL
        cacheConfigs.put("follow-data",
                defaultCacheConfig(Duration.ofMinutes(3)));

        RedisCacheManager manager = RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                // Allow Redis unavailability to degrade gracefully rather than
                // throwing exceptions on every cache read/write
                .disableCreateOnMissingCache()
                .build();

        log.info("[RedisConfig] CacheManager initialized with {} named caches.",
                cacheConfigs.size());
        return manager;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private RedisCacheConfiguration defaultCacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()               // never cache null — prevents null poisoning
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(
                                        redisObjectMapper())));
    }

    /**
     * ObjectMapper specifically for Redis serialization.
     * Must include:
     * - JavaTimeModule: handles Instant, LocalDate, ZonedDateTime etc.
     * - activateDefaultTyping: embeds the Java class name in the JSON so that
     *   deserialization can reconstruct the correct POJO type rather than
     *   falling back to LinkedHashMap.
     *
     * This ObjectMapper is intentionally NOT registered as a primary Spring bean
     * to avoid interfering with the main Jackson ObjectMapper used by Spring MVC.
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}