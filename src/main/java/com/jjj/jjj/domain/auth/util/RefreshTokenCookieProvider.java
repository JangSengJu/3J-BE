package com.jjj.jjj.domain.auth.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieProvider {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final Duration refreshTokenExpiration;

    public RefreshTokenCookieProvider(@Value("${jwt.refresh-expiration}") Duration refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken)
                .maxAge(refreshTokenExpiration)
                .build();
    }

    public ResponseCookie delete() {
        return base("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/");
    }
}
