package com.jjj.jjj.global.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-expiration}") Duration accessTokenExpiration,
            @Value("${jwt.refresh-expiration}") Duration refreshTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // Access Token 생성
    public String createAccessToken(Long id, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration.toMillis());

        return Jwts.builder()
                .setSubject(String.valueOf(id))
                .claim(ROLE_CLAIM, role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Refresh Token 생성 (회전/폐기용 jti 포함)
    public String createRefreshToken(Long id) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration.toMillis());

        return Jwts.builder()
                .setSubject(String.valueOf(id))
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 토큰에서 Authentication 객체 추출
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String role = claims.get(ROLE_CLAIM, String.class);

        User principal = new User(claims.getSubject(), "", Collections.singleton(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getMemberId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public Duration getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
