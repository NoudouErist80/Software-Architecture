package com.vibe.authservice;

import com.vibe.common.security.JwtUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtUtil} — token generation, claim extraction, and
 * validation. Pure logic, no Spring context required.
 */
class JwtUtilTest {

    private static final String SECRET =
            "test-secret-key-that-is-at-least-32-bytes-long-1234567890";
    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    @Test
    void generatesAccessTokenAndExtractsAllClaims() {
        String token = jwtUtil.generateAccessToken("user-1", "alice", "USER");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user-1");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("USER");
    }

    @Test
    void refreshTokenCarriesSubject() {
        String token = jwtUtil.generateRefreshToken("user-2");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user-2");
    }

    @Test
    void validateRejectsGarbageToken() {
        assertThat(jwtUtil.validateToken("this.is.not.a.jwt")).isFalse();
    }

    @Test
    void validateRejectsTokenSignedWithADifferentSecret() {
        JwtUtil other = new JwtUtil("a-completely-different-secret-key-also-32-bytes-long");
        String foreignToken = other.generateAccessToken("u", "n", "USER");

        assertThat(jwtUtil.validateToken(foreignToken)).isFalse();
    }

    @Test
    void validateRejectsExpiredToken() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil(SECRET, 1L, 1L); // 1ms access/refresh expiry
        String token = shortLived.generateAccessToken("u", "n", "USER");

        Thread.sleep(25);

        assertThat(shortLived.validateToken(token)).isFalse();
    }
}
