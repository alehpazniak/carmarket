package com.carmarket.gateway.security;

import com.carmarket.gateway.security.AccessRevocationChecker.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessRevocationCheckerTest {

    private static final String USER = "6f1c3b9e-0000-4000-8000-000000000001";
    private static final List<String> KEYS = List.of("auth:blocked:" + USER, "auth:min-iat:" + USER);

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final AccessRevocationChecker checker = new AccessRevocationChecker(redis);
    private final Instant issuedAt = Instant.ofEpochSecond(1_700_000_000L);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    private void redisReturns(String blocked, String minIat) {
        when(values.multiGet(KEYS)).thenReturn(Mono.just(Arrays.asList(blocked, minIat)));
    }

    @Test
    void noKeysMeansOk() {
        redisReturns(null, null);
        assertThat(checker.check(USER, issuedAt).block()).isEqualTo(Verdict.OK);
    }

    @Test
    void blockedWinsOverEverything() {
        redisReturns("1", null);
        assertThat(checker.check(USER, issuedAt).block()).isEqualTo(Verdict.BLOCKED);
    }

    @Test
    void tokensIssuedBeforeMinIatAreRevoked() {
        redisReturns(null, String.valueOf(issuedAt.getEpochSecond() + 1));
        assertThat(checker.check(USER, issuedAt).block()).isEqualTo(Verdict.REVOKED);
    }

    @Test
    void tokensIssuedAtOrAfterMinIatPass() {
        redisReturns(null, String.valueOf(issuedAt.getEpochSecond()));
        assertThat(checker.check(USER, issuedAt).block()).isEqualTo(Verdict.OK);
        assertThat(checker.check(USER, issuedAt.plusSeconds(60)).block()).isEqualTo(Verdict.OK);
    }

    @Test
    void aTokenWithoutIatIsRevokedOnceAMinIatExists() {
        redisReturns(null, String.valueOf(issuedAt.getEpochSecond()));
        assertThat(checker.check(USER, null).block()).isEqualTo(Verdict.REVOKED);
    }

    @Test
    void redisErrorsFailOpen() {
        when(values.multiGet(KEYS)).thenReturn(Mono.error(new RedisConnectionFailureException("down")));
        assertThat(checker.check(USER, issuedAt).block()).isEqualTo(Verdict.OK);
    }

    @Test
    void aHangingRedisFailsOpenAfterTheTimeout() {
        when(values.multiGet(KEYS)).thenReturn(Mono.never());
        long start = System.currentTimeMillis();
        assertThat(checker.check(USER, issuedAt).block(Duration.ofSeconds(3))).isEqualTo(Verdict.OK);
        assertThat(System.currentTimeMillis() - start).isLessThan(2000);
    }
}
