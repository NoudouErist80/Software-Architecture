package com.vibe.gateway.filter;

import com.vibe.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global JWT authentication filter for the VIBE API Gateway.
 *
 * <p>All downstream services trust the {@code X-User-Id}, {@code X-Username},
 * and {@code X-User-Role} headers injected here — they do NOT re-validate JWTs
 * themselves.  The original {@code Authorization} header is stripped before
 * forwarding so it never leaks to microservices.
 *
 * <p><b>WebSocket / SockJS note</b>: SockJS performs an initial HTTP GET to
 * {@code /ws/messaging/info} (no auth header, no Upgrade header) to determine
 * which transports are available.  This path is therefore included in
 * {@code PUBLIC_PATHS} so the filter does not reject it with 401.  The actual
 * STOMP CONNECT frame carries the JWT in its headers and is validated by the
 * messaging service's own {@code ChannelInterceptor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    /**
     * Paths that bypass JWT validation at the gateway level.
     *
     * <ul>
     *   <li>{@code /ws/messaging} — SockJS handshake + all transport paths
     *       (info, xhr, xhr-streaming, websocket).  The STOMP layer on the
     *       messaging-service enforces authentication independently.</li>
     *   <li>{@code /auth/register}, {@code /auth/login}, etc. — public auth
     *       endpoints that obviously can't require a JWT yet.</li>
     * </ul>
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/send-phone-otp",
            "/auth/verify-phone",
            "/ai/languages",
            "/actuator/",
            "/fallback",
            "/swagger-ui",
            "/v3/api-docs",
            "/ws/messaging",   // SockJS endpoint — all sub-paths (info, xhr, ws)
            "/media/files"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Bypass JWT validation for public paths
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            if (!jwtUtil.validateToken(token)) {
                return unauthorized(exchange, "Invalid or expired token");
            }
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return unauthorized(exchange, "Token validation failed");
        }

        String userId   = jwtUtil.extractUserId(token);
        String username = jwtUtil.extractUsername(token);
        String role     = jwtUtil.extractRole(token);

        // Forward user context as trusted internal headers; strip the raw JWT
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.set("X-User-Id",   userId);
                    h.set("X-Username",  username);
                    h.set("X-User-Role", role);
                    h.remove(HttpHeaders.AUTHORIZATION);
                }))
                .build();

        return chain.filter(mutated);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.warn("Unauthorized request to {}: {}", exchange.getRequest().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders()
                .set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        byte[] body = ("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + reason + "\"}")
                .getBytes();
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}