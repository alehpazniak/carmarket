package com.carmarket.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.List;

/**
 * Stateless JWT validation — no call to auth-service.
 * The gateway only verifies signature + expiry and extracts claims.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
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

    /**
     * Validates the JWT token and returns claims.
     * Throws JwtException on any validation failure.
     */
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
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return validateAndGetClaims(token).get("roles", List.class);
    }
}
