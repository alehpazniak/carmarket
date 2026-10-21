package com.carmarket.auction.client;

import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Apibara's quota headers from one response (every status, 429 included). Any field may be null when
 * Apibara didn't send it. periodStart/periodEnd bound the subscription period the counters belong to.
 */
public record ApibaraQuota(
    Integer limit,
    Integer used,
    Integer remaining,
    Instant periodStart,
    Instant periodEnd,
    Integer rateLimitRemaining,
    Integer perPageLimit,
    Instant observedAt
) {

    public static ApibaraQuota from(HttpHeaders headers, Instant observedAt) {
        return new ApibaraQuota(
            integer(headers.getFirst("X-Subscription-Limit")),
            integer(headers.getFirst("X-Subscription-Used")),
            integer(headers.getFirst("X-Subscription-Remaining")),
            instant(headers.getFirst("X-Subscription-Period-Start")),
            instant(headers.getFirst("X-Subscription-Period-End")),
            integer(headers.getFirst("X-RateLimit-Remaining")),
            integer(headers.getFirst("X-Per-Page-Limit")),
            observedAt);
    }

    /** True when the response carried none of the subscription counters (e.g. a proxy error page). */
    public boolean isEmpty() {
        return limit == null && used == null && remaining == null && periodStart == null && periodEnd == null;
    }

    static Integer integer(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
