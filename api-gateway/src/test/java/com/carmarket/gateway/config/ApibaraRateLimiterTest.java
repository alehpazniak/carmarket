package com.carmarket.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApibaraRateLimiterTest {

    @SuppressWarnings("unchecked")
    private final RateLimiter<RedisRateLimiter.Config> standard = mock(RateLimiter.class);
    @SuppressWarnings("unchecked")
    private final RateLimiter<RedisRateLimiter.Config> perIp = mock(RateLimiter.class);
    @SuppressWarnings("unchecked")
    private final RateLimiter<RedisRateLimiter.Config> global = mock(RateLimiter.class);
    private final ApibaraRateLimiter limiter = new ApibaraRateLimiter(standard, perIp, global);

    @Test
    void loggedInUsersKeepTheStandardBucket() {
        when(standard.isAllowed("route", "u1")).thenReturn(Mono.just(allowed(true)));

        assertThat(limiter.isAllowed("route", "u1").block().isAllowed()).isTrue();
        verify(perIp, never()).isAllowed(any(), any());
        verify(global, never()).isAllowed(any(), any());
    }

    @Test
    void anonymousCallersNeedTheirOwnAndTheGlobalBucket() {
        when(perIp.isAllowed("route", "apibara-anon:1.2.3.4")).thenReturn(Mono.just(allowed(true)));
        when(global.isAllowed("route", ApibaraRateLimiter.GLOBAL_ANONYMOUS_KEY)).thenReturn(Mono.just(allowed(true)));
        assertThat(limiter.isAllowed("route", "apibara-anon:1.2.3.4").block().isAllowed()).isTrue();

        when(global.isAllowed("route", ApibaraRateLimiter.GLOBAL_ANONYMOUS_KEY)).thenReturn(Mono.just(allowed(false)));
        assertThat(limiter.isAllowed("route", "apibara-anon:1.2.3.4").block().isAllowed()).isFalse();
        verify(standard, never()).isAllowed(any(), any());
    }

    @Test
    void anIpOverItsLimitDoesNotSpendTheGlobalBucket() {
        when(perIp.isAllowed("route", "apibara-anon:1.2.3.4")).thenReturn(Mono.just(allowed(false)));

        assertThat(limiter.isAllowed("route", "apibara-anon:1.2.3.4").block().isAllowed()).isFalse();
        verify(global, never()).isAllowed(any(), any());
    }

    @Test
    void perIpLimiterAllowsOneRequestASecondWithABurstOfFive() {
        // Burst 5: the vehicle page sends 3 lookups at once
        RedisRateLimiter.Config config = config(new GatewayConfig().apibaraAnonymousIpRateLimiter(1, 5));

        assertThat(config.getReplenishRate()).isEqualTo(1);
        assertThat(config.getBurstCapacity()).isEqualTo(5);
        assertThat(config.getRequestedTokens()).isEqualTo(1);
    }

    @Test
    void sharedLimiterAllowsExactlyFiftyRequestsPerThirtyDaysFromAFullBucket() {
        RedisRateLimiter.Config config = config(new GatewayConfig().apibaraAnonymousGlobalRateLimiter(50));

        assertThat(config.getReplenishRate()).isEqualTo(1);
        assertThat(config.getRequestedTokens()).isEqualTo(51_840);
        assertThat(config.getBurstCapacity()).isEqualTo(2_592_000);
        assertThat(config.getBurstCapacity() / config.getRequestedTokens()).isEqualTo(50);
        assertThat(config.getBurstCapacity() % config.getRequestedTokens()).isZero();
        // Refilled at 1 token/s, an empty bucket is full again after exactly 30 days
        assertThat(config.getBurstCapacity() / config.getReplenishRate()).isEqualTo(ApibaraRateLimiter.THIRTY_DAYS_SECONDS);
    }

    @Test
    void perPeriodSpendsTheWholePeriodOnTheGivenNumberOfRequests() {
        assertThat(ApibaraRateLimiter.tokensPerRequest(50, ApibaraRateLimiter.THIRTY_DAYS_SECONDS)).isEqualTo(51_840);
        assertThat(ApibaraRateLimiter.tokensPerRequest(0, 3600)).isEqualTo(3600);
        // More requests than seconds in the period: one token each, capacity = requests
        RedisRateLimiter.Config big = config(ApibaraRateLimiter.perPeriod(5_000_000, ApibaraRateLimiter.THIRTY_DAYS_SECONDS));
        assertThat(big.getBurstCapacity() / big.getRequestedTokens()).isEqualTo(5_000_000);
    }

    static RedisRateLimiter.Config config(RedisRateLimiter limiter) {
        return (RedisRateLimiter.Config) ReflectionTestUtils.getField(limiter, "defaultConfig");
    }

    private static Response allowed(boolean allowed) {
        return new Response(allowed, Map.of());
    }
}
