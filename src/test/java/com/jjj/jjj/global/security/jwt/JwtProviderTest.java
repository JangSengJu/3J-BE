package com.jjj.jjj.global.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            "test-secret-test-secret-test-secret-1234567890",
            Duration.ofMinutes(30),
            Duration.ofDays(14)
    );

    @Test
    @DisplayName("Access 토큰은 validateAccessToken을 통과한다")
    void accessTokenIsAccepted() {
        String accessToken = jwtProvider.createAccessToken(1L, "USER");

        assertThat(jwtProvider.validateAccessToken(accessToken)).isTrue();
    }

    @Test
    @DisplayName("Refresh 토큰은 validateAccessToken에서 거부된다")
    void refreshTokenIsRejectedAsAccessToken() {
        String refreshToken = jwtProvider.createRefreshToken(1L);

        assertThat(jwtProvider.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 거부된다")
    void tokenSignedWithOtherKeyIsRejected() {
        JwtProvider other = new JwtProvider(
                "another-secret-another-secret-another-1234567890",
                Duration.ofMinutes(30),
                Duration.ofDays(14)
        );
        String foreignToken = other.createAccessToken(1L, "USER");

        assertThat(jwtProvider.validateAccessToken(foreignToken)).isFalse();
    }
}
