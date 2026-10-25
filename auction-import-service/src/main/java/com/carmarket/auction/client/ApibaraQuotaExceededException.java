package com.carmarket.auction.client;

import java.time.Duration;

/** Apibara answered 429: the monthly quota or the short-window rate limit is used up. */
public class ApibaraQuotaExceededException extends RuntimeException {

    private final transient Duration retryAfter;
    private final transient ApibaraQuota quota;

    public ApibaraQuotaExceededException(Duration retryAfter, ApibaraQuota quota) {
        super("Apibara quota exhausted" + (retryAfter != null ? ", retry after " + retryAfter.toSeconds() + " s" : ""));
        this.retryAfter = retryAfter;
        this.quota = quota;
    }

    /** From the Retry-After header; null when Apibara didn't send one. */
    public Duration getRetryAfter() {
        return retryAfter;
    }

    public ApibaraQuota getQuota() {
        return quota;
    }

    /**
     * The monthly quota, not the short-window rate limit: the 429 said X-Subscription-Remaining 0. Without that
     * header Apibara's answer doesn't tell, and it counts as the short-term limit (it may pass by itself).
     */
    public boolean isMonthlyQuotaExhausted() {
        return quota != null && quota.remaining() != null && quota.remaining() <= 0;
    }
}
