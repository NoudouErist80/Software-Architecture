package com.vibe.rewardsservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Kafka consumer wiring for the rewards service.
 *
 * <p>Platform producers publish events as plain JSON with
 * {@code spring.json.add.type.headers=false}, so the consumer cannot rely on a
 * type header to pick a target class. Instead we read each record value as a
 * raw JSON {@code String} (see {@code value-deserializer} in application.yml)
 * and let this {@link StringJsonMessageConverter} convert it to whatever type
 * each {@code @KafkaListener} method declares — {@code TokenEarnedEvent} for the
 * token topic, {@code Map} for the message/video topics. Spring Boot auto-detects
 * a single {@link RecordMessageConverter} bean and applies it to the
 * auto-configured listener container factory.
 *
 * <p>This also makes consumption resilient: a {@code StringDeserializer} never
 * throws on a malformed payload, so one bad record can no longer wedge the
 * consumer in an infinite {@code RecordDeserializationException} retry loop.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public RecordMessageConverter jsonRecordConverter() {
        return new StringJsonMessageConverter();
    }
}
