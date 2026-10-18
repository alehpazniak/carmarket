package com.carmarket.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
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
    void perPeriodSpendsTheWholePeriodOnTheGivenNumberOfRequests() {
        // Refilled at 1 token/s: one request's worth every 720 s = 5 an hour; 2880 s = 30 a day
        assertThat(ApibaraRateLimiter.tokensPerRequest(5, 3600)).isEqualTo(720);
        assertThat(ApibaraRateLimiter.tokensPerRequest(30, 86400)).isEqualTo(2880);
        assertThat(ApibaraRateLimiter.tokensPerRequest(0, 3600)).isEqualTo(3600);
        assertThat(ApibaraRateLimiter.perPeriod(5, 3600)).isNotNull();
    }

    private static Response allowed(boolean allowed) {
        return new Response(allowed, Map.of());
    }
}
