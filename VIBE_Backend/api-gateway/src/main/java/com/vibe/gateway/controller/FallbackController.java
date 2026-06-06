package com.vibe.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
public class FallbackController {
    @RequestMapping("/fallback")
    public Mono<ResponseEntity<Map<String, Object>>> fallback() {
        log.warn("Gateway circuit breaker triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "error", "Service Temporarily Unavailable",
                        "message", "VIBE is experiencing high load. Please retry shortly.",
                        "timestamp", Instant.now().toString(),
                        "retryAfter", 5)));
    }
}
