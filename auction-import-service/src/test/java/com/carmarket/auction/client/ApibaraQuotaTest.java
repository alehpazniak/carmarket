package com.carmarket.auction.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApibaraQuotaTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void readsEveryHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Subscription-Limit", "100");
        headers.add("X-Subscription-Used", "58");
        headers.add("X-Subscription-Remaining", " 42 ");
        headers.add("X-Subscription-Period-Start", "2026-10-01T00:00:00+02:00");
        headers.add("X-Subscription-Period-End", "2026-10-31T23:59:59Z");
        headers.add("X-RateLimit-Remaining", "9");
        headers.add("X-Per-Page-Limit", "20");

        assertThat(ApibaraQuota.from(headers, NOW)).isEqualTo(new ApibaraQuota(100, 58, 42,
            Instant.parse("2026-09-30T22:00:00Z"), Instant.parse("2026-10-31T23:59:59Z"), 9, 20, NOW));
    }

    @Test
    void missingOrGarbledHeadersAreNull() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Subscription-Remaining", "lots");
        headers.add("X-Subscription-Period-Start", "October");

        ApibaraQuota quota = ApibaraQuota.from(headers, NOW);
        assertThat(quota.remaining()).isNull();
        assertThat(quota.periodStart()).isNull();
        assertThat(quota.isEmpty()).isTrue();
        assertThat(quota.observedAt()).isEqualTo(NOW);
    }
}
