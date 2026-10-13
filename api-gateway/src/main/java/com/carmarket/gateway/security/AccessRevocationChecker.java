package com.carmarket.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Admin actions must not wait for the 15-minute access token to expire. auth-service writes two keys
 * into the shared Redis, read here in one MGET per authenticated request:
 * <ul>
 *   <li>{@code auth:blocked:{userId}} — the account is blocked → {@link Verdict#BLOCKED}.</li>
 *   <li>{@code auth:min-iat:{userId}} — epoch seconds; a token issued earlier (role change, block,
 *       admin logout) → {@link Verdict#REVOKED}. The client's refresh then gets a token with the
 *       current role, or 403 if the account is blocked.</li>
 * </ul>
 * Fails open: if Redis errors or is slower than {@link #TIMEOUT}, the request goes through with a
 * WARN (like the rate limiter). Blocked users still can't refresh, since auth-service checks the DB.
 */
@Slf4j
@Component
public class AccessRevocationChecker {

    public enum Verdict { OK, BLOCKED, REVOKED }

    static final String BLOCKED_PREFIX = "auth:blocked:";
    static final String MIN_IAT_PREFIX = "auth:min-iat:";
    static final Duration TIMEOUT = Duration.ofMillis(500);

    private final ReactiveStringRedisTemplate redis;

    public AccessRevocationChecker(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Verdict> check(String userId, Instant issuedAt) {
        return redis.opsForValue().multiGet(List.of(BLOCKED_PREFIX + userId, MIN_IAT_PREFIX + userId))
            .timeout(TIMEOUT)
            .map(values -> verdict(values, issuedAt))
            .defaultIfEmpty(Verdict.OK)
            .onErrorResume(e -> {
                log.warn("Revocation check skipped for user {} (Redis unavailable: {})", userId, e.toString());
                return Mono.just(Verdict.OK);
            });
    }

    private static Verdict verdict(List<String> values, Instant issuedAt) {
        if (values.size() > 0 && values.get(0) != null) {
            return Verdict.BLOCKED;
        }
        String minIat = values.size() > 1 ? values.get(1) : null;
        if (minIat != null) {
            try {
                // JWT iat has second precision; a token from the same second as the change still passes
                if (issuedAt == null || issuedAt.getEpochSecond() < Long.parseLong(minIat.trim())) {
                    return Verdict.REVOKED;
                }
            } catch (NumberFormatException e) {
                log.warn("Ignoring malformed min-iat value: {}", minIat);
            }
        }
        return Verdict.OK;
    }
}
