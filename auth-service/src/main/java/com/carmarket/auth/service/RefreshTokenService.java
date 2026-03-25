package com.carmarket.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages refresh tokens in Redis.
 *
 * Redis key: "refresh:{userId}:{jti}"  →  value: "valid"
 * TTL matches the token expiry (7 days).
 *
 * On logout or token rotation — the old key is deleted (revoked).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private final StringRedisTemplate redisTemplate;

    public void store(String userId, String jti, long expiryMs) {
        String key = buildKey(userId, jti);
        redisTemplate.opsForValue().set(key, "valid", Duration.ofMillis(expiryMs));
        log.debug("Stored refresh token for user: {} jti: {}", userId, jti);
    }

    public boolean isValid(String userId, String jti) {
        String key = buildKey(userId, jti);
        return redisTemplate.hasKey(key);
    }

    /**
     * Revoke a specific token (logout or rotation).
     */
    public void revoke(String userId, String jti) {
        String key = buildKey(userId, jti);
        redisTemplate.delete(key);
        log.debug("Revoked refresh token for user: {} jti: {}", userId, jti);
    }

    /**
     * Revoke ALL refresh tokens for a user (e.g. password change, account compromise).
     */
    public void revokeAll(String userId) {
        String pattern = KEY_PREFIX + userId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Revoked {} refresh tokens for user: {}", keys.size(), userId);
        }
    }

    private String buildKey(String userId, String jti) {
        return KEY_PREFIX + userId + ":" + jti;
    }
}
