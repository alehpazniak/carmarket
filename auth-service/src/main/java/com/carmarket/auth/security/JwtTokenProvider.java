package com.carmarket.auth.security;

import com.carmarket.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates JWTs.
 *
 * Access token — short-lived (15 min), carries user id, email, role
 * Refresh token — long-lived (7 days), carries only user id + jti for revocation
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.access-token-expiry-ms:900000}")       // 15 min
    private long accessTokenExpiryMs;

    @Getter
    @Value("${jwt.refresh-token-expiry-ms:604800000}")   // 7 days
    private long refreshTokenExpiryMs;

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "jwt.secret is not configured — refusing to start");
        }
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) { // HMAC-SHA256 requires >= 256 bit
            throw new IllegalStateException(
                "jwt.secret too short — must be at least 32 bytes after base64 decode");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ─── ACCESS TOKEN ──────────────────────────────────────────────────────────

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", List.of(user.getRole().name()))
            .claim("name", user.getDisplayName())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
            .signWith(signingKey)
            .compact();
    }

    // ─── REFRESH TOKEN ─────────────────────────────────────────────────────────

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString(); // unique token id for revocation
        return Jwts.builder()
            .subject(user.getId().toString())
            .id(jti)
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(refreshTokenExpiryMs)))
            .signWith(signingKey)
            .compact();
    }

    // ─── VALIDATION ────────────────────────────────────────────────────────────

    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateAndGetClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String extractUserId(String token) {
        return validateAndGetClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return validateAndGetClaims(token).getId();
    }

}
