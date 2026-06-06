package com.vibe.messagingservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade {@link WebClient} configuration for the VIBE Messaging Service.
 *
 * <h3>Why this class is needed</h3>
 * {@code AiSummaryService} injects {@link WebClient.Builder} by constructor.
 * Spring Boot auto-configures a default {@link WebClient.Builder} bean when
 * {@code spring-boot-starter-webflux} is on the classpath — but its defaults
 * (unlimited connections, 30 s connect timeout) are not suitable for a service
 * that must handle billions of concurrent users.  This class replaces those
 * defaults with production-tuned settings.
 *
 * <h3>Key design decisions (Telegram / Discord / WhatsApp scale)</h3>
 * <ul>
 *   <li><b>Fixed connection pool</b> – max 500 connections per remote host,
 *       1 000 queued-acquire slots.  Requests that exceed the queue are rejected
 *       immediately (fast-fail) rather than waiting indefinitely.</li>
 *   <li><b>2 s connect timeout</b> – Netty default is 30 s; a 30 s stall on the
 *       event loop under burst traffic would cascade into a global outage.</li>
 *   <li><b>10 s read / write timeouts</b> – AI responses can be large; 10 s is
 *       generous but bounded.  The per-request timeout in {@link AiSummaryService}
 *       adds an additional layer of protection.</li>
 *   <li><b>Keep-alive + idle eviction (30 s)</b> – persistent HTTP/1.1 connections
 *       eliminate TLS handshake overhead for repeated AI calls; idle eviction
 *       prevents TCP RST races from the server side.</li>
 *   <li><b>Codec buffer 10 MB</b> – AI summary payloads for long conversations
 *       can exceed the 256 KB default; 10 MB is safe and still bounded.</li>
 *   <li><b>Micrometer metrics</b> – pool stats (active, idle, pending) flow to
 *       Prometheus/Grafana automatically.  Enables SLA dashboards without extra code.</li>
 *   <li><b>Compression</b> – reduces AI transcript bandwidth by ≈70 % on text.</li>
 * </ul>
 *
 * <h3>Next step for 9-figure DAU</h3>
 * Add Resilience4j circuit-breaker wrapping each WebClient call, and replace
 * HTTP/1.1 with gRPC for internal service-to-service communication.
 */
@Configuration
public class WebClientConfig {

    // ── Tuneable via application.yml / environment variables ──────────────────

    @Value("${vibe.webclient.max-connections:500}")
    private int maxConnections;

    @Value("${vibe.webclient.pending-acquire-max:1000}")
    private int pendingAcquireMax;

    @Value("${vibe.webclient.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${vibe.webclient.read-timeout-s:10}")
    private int readTimeoutS;

    @Value("${vibe.webclient.write-timeout-s:10}")
    private int writeTimeoutS;

    @Value("${vibe.webclient.idle-timeout-s:30}")
    private int idleTimeoutS;

    @Value("${vibe.webclient.max-lifetime-s:60}")
    private int maxLifetimeS;

    /**
     * Registers a customised {@link WebClient.Builder} bean.
     *
     * <p>Spring Boot's auto-configuration backs off when a custom
     * {@link WebClient.Builder} bean is present, so this bean is the single
     * source of truth for all outbound HTTP configuration in this service.
     *
     * <p>Callers (e.g. {@link AiSummaryService}) receive this builder via
     * constructor injection and call {@code .baseUrl(...).build()} to create
     * their own immutable {@link WebClient} instance.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {

        // ── 1. Netty connection pool ───────────────────────────────────────
        ConnectionProvider pool = ConnectionProvider.builder("vibe-ai-http-pool")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMax)
                // Fail requests that cannot acquire a connection within 5 s.
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                // Evict connections idle longer than idleTimeoutS to avoid server-side RST.
                .maxIdleTime(Duration.ofSeconds(idleTimeoutS))
                // Cap individual connection lifetime (HTTP keep-alive refresh boundary).
                .maxLifeTime(Duration.ofSeconds(maxLifetimeS))
                // Background eviction run every 30 s — avoids blocking request threads.
                .evictInBackground(Duration.ofSeconds(30))
                // Expose pool metrics to Micrometer (active, idle, pending gauges).
                .metrics(true)
                .build();

        // ── 2. Netty HttpClient with timeout handlers ─────────────────────
        HttpClient httpClient = HttpClient.create(pool)
                // TCP connect timeout — fail before Netty's default 30 s elapses.
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                // Keep-alive on the TCP socket (Netty default, made explicit).
                .option(ChannelOption.SO_KEEPALIVE, true)
                // Pipeline-level read/write timeouts via Netty channel handlers.
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutS, TimeUnit.SECONDS)))
                // Gzip compression — AI transcript + response payloads compress ≈70 %.
                .compress(true)
                // Follow HTTP 3xx redirects (rare for AI services, but safe).
                .followRedirect(true);

        // ── 3. Assemble WebClient.Builder ─────────────────────────────────
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Raise the in-memory codec buffer to 10 MB.
                // Default 256 KB is too small for long AI summarisation payloads.
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                // Structured, non-blocking request/response logging for observability.
                .filter(logRequest())
                .filter(logResponse());
    }

    // ─── Private logging filters ──────────────────────────────────────────────

    /**
     * Non-blocking request logger — runs on the Netty event loop, not a thread pool.
     * Logs at DEBUG to avoid noise in production; enable via:
     * {@code logging.level.com.vibe.messagingservice.config.WebClientConfig: DEBUG}
     */
    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            if (LoggerFactory.getLogger(WebClientConfig.class).isDebugEnabled()) {
                LoggerFactory.getLogger(WebClientConfig.class)
                        .debug("[WebClient] → {} {}", req.method(), req.url());
            }
            return Mono.just(req);
        });
    }

    /**
     * Non-blocking response status logger.
     */
    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(resp -> {
            if (LoggerFactory.getLogger(WebClientConfig.class).isDebugEnabled()) {
                LoggerFactory.getLogger(WebClientConfig.class)
                        .debug("[WebClient] ← {}", resp.statusCode());
            }
            return Mono.just(resp);
        });
    }
}