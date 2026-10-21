package com.carmarket.auction.client;

import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.repository.SyncRunRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The latest Apibara quota snapshot the sync has seen, in memory. After a restart it falls back to the
 * last one persisted in sync_runs. Only the sync's ApibaraClient.searchVehiclesPage feeds it; the live
 * lookups don't, so the counters can be ahead of the snapshot until the next sync request.
 */
@Component
public class ApibaraQuotaTracker {

    private final SyncRunRepository runs;
    private final AtomicReference<ApibaraQuota> latest = new AtomicReference<>();
    private final AtomicLong requestsSent = new AtomicLong();
    private volatile Instant retryNotBefore;

    public ApibaraQuotaTracker(SyncRunRepository runs) {
        this.runs = runs;
    }

    /** Called before every sync request is sent, whatever its outcome. */
    public void requestSent() {
        requestsSent.incrementAndGet();
    }

    /** Monotonic count of sync requests since startup; the scheduler stores the difference per run. */
    public long requestsSent() {
        return requestsSent.get();
    }

    public void record(ApibaraQuota quota) {
        if (quota != null && !quota.isEmpty()) {
            latest.set(quota);
        }
    }

    public void quotaExhausted(Instant retryNotBefore) {
        this.retryNotBefore = retryNotBefore;
    }

    /** From the last 429's Retry-After; null when none was seen. */
    public Instant retryNotBefore() {
        return retryNotBefore;
    }

    public Optional<ApibaraQuota> latest() {
        ApibaraQuota quota = latest.get();
        if (quota != null) {
            return Optional.of(quota);
        }
        Optional<ApibaraQuota> persisted = runs.findFirstByQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc()
            .map(ApibaraQuotaTracker::fromRun);
        persisted.ifPresent(q -> latest.compareAndSet(null, q));
        return persisted;
    }

    private static ApibaraQuota fromRun(SyncRun run) {
        return new ApibaraQuota(run.getQuotaLimit(), run.getQuotaUsed(), run.getQuotaRemaining(),
            run.getQuotaPeriodStart(), run.getQuotaPeriodEnd(), null, null, run.getQuotaObservedAt());
    }
}
