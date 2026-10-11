package com.carmarket.importorder.security;

import com.carmarket.importorder.config.ImportOrderProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * X-Order-Token for the client page: HS256 with this service's own secret (not the user JWT secret).
 * Claims: sub = order id, ver = access_version at login, jti = token id (for the VIEW log), exp.
 */
@Component
public class ClientTokenService {

    private static final String VERSION_CLAIM = "ver";

    private final SecretKey key;
    private final ImportOrderProperties.Token config;

    public ClientTokenService(ImportOrderProperties properties) {
        this.config = properties.token();
        String secret = config.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("import-order.token.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record ClientToken(UUID orderId, int accessVersion, String tokenId) {
    }

    public IssuedToken issue(UUID orderId, int accessVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(config.ttl());
        String token = Jwts.builder()
            .subject(orderId.toString())
            .claim(VERSION_CLAIM, accessVersion)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** The token's claims if the signature is ours and it hasn't expired; empty otherwise. */
    public Optional<ClientToken> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Integer version = claims.get(VERSION_CLAIM, Integer.class);
            if (version == null || claims.getSubject() == null || claims.getId() == null) return Optional.empty();
            return Optional.of(new ClientToken(UUID.fromString(claims.getSubject()), version, claims.getId()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
