package com.vibe.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Kafka consumer wiring for the notification service.
 *
 * <p>Platform producers publish events as plain JSON with
 * {@code spring.json.add.type.headers=false}, so the consumer cannot rely on a
 * type header to choose a target class. The value is therefore read as a raw
 * JSON {@code String} (see {@code value-deserializer} in application.yml) and
 * this {@link StringJsonMessageConverter} converts it to whatever type each
 * {@code @KafkaListener} declares — {@code TokenEarnedEvent},
 * {@code NotificationEvent}, or {@code Map}. Spring Boot auto-detects a single
 * {@link RecordMessageConverter} bean and applies it to the auto-configured
 * listener container factory.
 *
 * <p>A {@code StringDeserializer} also never throws on a malformed payload, so a
 * single bad record can no longer wedge the consumer in an infinite
 * {@code RecordDeserializationException} retry loop.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public RecordMessageConverter jsonRecordConverter() {
        return new StringJsonMessageConverter();
    }
}
