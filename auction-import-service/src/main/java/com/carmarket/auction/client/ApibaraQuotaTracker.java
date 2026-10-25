package com.carmarket.auction.client;

import com.carmarket.auction.config.ApibaraSyncProperties;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.repository.SyncRunRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The latest Apibara quota snapshot, in memory, fed by every ApibaraClient response (the sync's, the live lookups'
 * and GET /usage). After a restart it falls back to the last one persisted in sync_runs for the same API key
 * (quota_key_fingerprint). A snapshot is ignored once it can't describe the quota any more: its period has ended,
 * or it has no period end and is older than apibara.sync.quota-snapshot-max-age. Otherwise a snapshot with
 * remaining 0 (e.g. from a 429) would block the sync forever, since only a sent request refreshes it.
 */
@Component
public class ApibaraQuotaTracker {

    private final SyncRunRepository runs;
    private final ParserTargetsConfig config;
    private final ApibaraSyncProperties props;
    private final Clock clock;
    private final AtomicReference<ApibaraQuota> latest = new AtomicReference<>();
    private final AtomicLong requestsSent = new AtomicLong();
    private volatile Instant retryNotBefore;

    public ApibaraQuotaTracker(SyncRunRepository runs, ParserTargetsConfig config, ApibaraSyncProperties props, Clock clock) {
        this.runs = runs;
        this.config = config;
        this.props = props;
        this.clock = clock;
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

    /** The latest snapshot that still describes the quota of the configured key; empty when there is none. */
    public Optional<ApibaraQuota> latest() {
        ApibaraQuota quota = latest.get();
        if (quota == null) {
            String fingerprint = keyFingerprint();
            quota = fingerprint == null ? null
                : runs.findFirstByQuotaKeyFingerprintAndQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc(fingerprint)
                    .map(ApibaraQuotaTracker::fromRun).orElse(null);
            if (quota != null) {
                latest.compareAndSet(null, quota);
            }
        }
        return Optional.ofNullable(quota).filter(q -> current(q, clock.instant()));
    }

    /**
     * The first 12 hex characters of the SHA-256 of apibara.api-key, stored with a persisted snapshot so a snapshot
     * of another key (or plan) is never used; never the key itself. Null without a key.
     */
    public String keyFingerprint() {
        return fingerprint(config.getApiKey());
    }

    static String fingerprint(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JRE has SHA-256
        }
    }

    private boolean current(ApibaraQuota quota, Instant now) {
        if (quota.periodEnd() != null) {
            return now.isBefore(quota.periodEnd());
        }
        return quota.observedAt() != null && !quota.observedAt().isBefore(now.minus(props.getQuotaSnapshotMaxAge()));
    }

    private static ApibaraQuota fromRun(SyncRun run) {
        return new ApibaraQuota(run.getQuotaLimit(), run.getQuotaUsed(), run.getQuotaRemaining(),
            run.getQuotaPeriodStart(), run.getQuotaPeriodEnd(), null, null, run.getQuotaObservedAt());
    }
}
