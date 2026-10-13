package com.carmarket.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Makes admin actions take effect before the 15-minute access tokens expire. The gateway reads both
 * keys (same Redis) on every authenticated request:
 * <ul>
 *   <li>{@code auth:blocked:{userId}} — present while the account is blocked (no TTL) → 401.</li>
 *   <li>{@code auth:min-iat:{userId}} — epoch seconds; tokens issued earlier are rejected with 401,
 *       so the client refreshes and gets the new role (or 403 when blocked). Lives as long as the
 *       longest-lived access token, plus a minute.</li>
 * </ul>
 * Refresh tokens are checked against the database, so they need no key here.
 */
@Slf4j
@Service
public class AccessRevocationService {

    static final String BLOCKED_PREFIX = "auth:blocked:";
    static final String MIN_IAT_PREFIX = "auth:min-iat:";

    private final StringRedisTemplate redis;
    private final Duration minIatTtl;

    public AccessRevocationService(StringRedisTemplate redis, com.carmarket.auth.security.JwtTokenProvider tokens) {
        this.redis = redis;
        this.minIatTtl = Duration.ofMillis(tokens.getAccessTokenExpiryMs()).plusMinutes(1);
    }

    /** Access tokens issued before now stop working. */
    public void invalidateIssuedTokens(String userId) {
        redis.opsForValue().set(MIN_IAT_PREFIX + userId, String.valueOf(Instant.now().getEpochSecond()), minIatTtl);
    }

    public void markBlocked(String userId) {
        redis.opsForValue().set(BLOCKED_PREFIX + userId, "1");
        invalidateIssuedTokens(userId);
    }

    public void clearBlocked(String userId) {
        redis.delete(BLOCKED_PREFIX + userId);
    }
}
