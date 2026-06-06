package com.vibe.aiservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ClaudeApiConfig {

    @Value("${vibe.ai.claude.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public HttpClient claudeHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
