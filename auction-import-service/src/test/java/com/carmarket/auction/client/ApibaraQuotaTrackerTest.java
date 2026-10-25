package com.carmarket.auction.client;

import com.carmarket.auction.config.ApibaraSyncProperties;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.repository.SyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which snapshot still describes the quota: not after its period, not too old without one, never another key's. */
class ApibaraQuotaTrackerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final String KEY = "ak_test_current";

    private final SyncRunRepository runs = mock(SyncRunRepository.class);
    private final ParserTargetsConfig config = new ParserTargetsConfig();
    private final ApibaraQuotaTracker tracker = new ApibaraQuotaTracker(runs, config, new ApibaraSyncProperties(),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        config.setApiKey(KEY);
        when(runs.findFirstByQuotaKeyFingerprintAndQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc(anyString()))
            .thenReturn(Optional.empty());
    }

    private static ApibaraQuota quota(int remaining, String periodEnd, Instant observedAt) {
        return new ApibaraQuota(100, 100 - remaining, remaining, null, periodEnd == null ? null : Instant.parse(periodEnd),
            null, null, observedAt);
    }

    private static SyncRun persisted(int remaining, String periodEnd, Instant observedAt) {
        SyncRun run = SyncRun.start("FeedSync", "SCHEDULED");
        run.setQuotaRemaining(remaining);
        run.setQuotaPeriodEnd(periodEnd == null ? null : Instant.parse(periodEnd));
        run.setQuotaObservedAt(observedAt);
        return run;
    }

    @Test
    void aSnapshotInsideItsPeriodCounts() {
        tracker.record(quota(0, "2026-11-05T10:00:00Z", NOW.minusSeconds(20 * 3600)));
        assertThat(tracker.latest()).map(ApibaraQuota::remaining).contains(0);
    }

    /** remaining 0 from October must not block November */
    @Test
    void aSnapshotWhosePeriodEndedIsIgnored() {
        tracker.record(quota(0, "2026-11-01T00:00:00Z", NOW.minusSeconds(3 * 86400)));
        assertThat(tracker.latest()).isEmpty();
    }

    /** a 429 sends no period end: such a snapshot counts for quota-snapshot-max-age (6 h) */
    @Test
    void aSnapshotWithoutPeriodEndCountsOnlyUntilTheMaxAge() {
        tracker.record(quota(0, null, NOW.minusSeconds(5 * 3600)));
        assertThat(tracker.latest()).isPresent();

        tracker.record(quota(0, null, NOW.minusSeconds(7 * 3600)));
        assertThat(tracker.latest()).isEmpty();
    }

    /** the restart case that blocked the sync for good: the persisted 429 snapshot, days old */
    @Test
    void anOldPersistedSnapshotIsIgnoredAfterARestart() {
        when(runs.findFirstByQuotaKeyFingerprintAndQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc(tracker.keyFingerprint()))
            .thenReturn(Optional.of(persisted(0, null, NOW.minusSeconds(2 * 86400))));
        assertThat(tracker.latest()).isEmpty();
    }

    @Test
    void onlyTheConfiguredKeysSnapshotIsReadBack() {
        String current = tracker.keyFingerprint();
        when(runs.findFirstByQuotaKeyFingerprintAndQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc(current))
            .thenReturn(Optional.of(persisted(42, "2026-11-30T00:00:00Z", NOW.minusSeconds(3600))));
        assertThat(tracker.latest()).map(ApibaraQuota::remaining).contains(42);

        // another key (a new plan): its fingerprint finds nothing, the old key's snapshot is never used
        ApibaraQuotaTracker otherKey = new ApibaraQuotaTracker(runs, keyed("ak_test_other"), new ApibaraSyncProperties(),
            Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(otherKey.keyFingerprint()).isNotEqualTo(current);
        assertThat(otherKey.latest()).isEmpty();
    }

    @Test
    void theFingerprintIsTwelveHexCharactersAndNeverTheKey() {
        String fingerprint = tracker.keyFingerprint();
        assertThat(fingerprint).matches("[0-9a-f]{12}").doesNotContain(KEY);
        assertThat(ApibaraQuotaTracker.fingerprint(KEY)).isEqualTo(fingerprint); // stable across restarts

        config.setApiKey(" ");
        assertThat(tracker.keyFingerprint()).isNull();
        assertThat(tracker.latest()).isEmpty();
        verify(runs, never()).findFirstByQuotaKeyFingerprintAndQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc(null);
    }

    private static ParserTargetsConfig keyed(String key) {
        ParserTargetsConfig other = new ParserTargetsConfig();
        other.setApiKey(key);
        return other;
    }
}
