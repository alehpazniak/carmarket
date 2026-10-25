package com.carmarket.auction.sync;

import com.carmarket.auction.client.ApibaraQuota;
import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.config.ApibaraSyncProperties;
import com.carmarket.auction.repository.SyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncBudgetTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final ApibaraSyncProperties props = new ApibaraSyncProperties();
    private final SyncRunRepository runs = mock(SyncRunRepository.class);
    private final ApibaraQuotaTracker tracker = mock(ApibaraQuotaTracker.class);

    @BeforeEach
    void freePlan() {
        props.setMonthlyBudget(60);
        props.setKeepRemaining(10);
        when(tracker.latest()).thenReturn(Optional.empty());
    }

    /** spentBefore before today (in the period), spentToday since midnight */
    private SyncBudget.Allowance at(String instant, long spentBefore, long spentToday) {
        Instant now = Instant.parse(instant);
        Instant midnight = now.atZone(WARSAW).toLocalDate().atStartOfDay(WARSAW).toInstant();
        doAnswer(inv -> inv.<Instant>getArgument(0).isBefore(midnight) ? spentBefore + spentToday : spentToday)
            .when(runs).sumApiRequestsSince(any());
        return new SyncBudget(props, runs, tracker, Clock.fixed(now, WARSAW)).today();
    }

    private static ApibaraQuota quota(Integer remaining, String periodStart, String periodEnd) {
        return new ApibaraQuota(100, null, remaining, periodStart == null ? null : Instant.parse(periodStart),
            periodEnd == null ? null : Instant.parse(periodEnd), null, null, Instant.parse("2026-10-24T08:00:00Z"));
    }

    @Test
    void theBudgetLeftIsSpreadOverTheDaysLeftTodayIncluded() {
        // 24 Oct: 8 days left in October, 60 / 8 = 7
        SyncBudget.Allowance allowance = at("2026-10-24T06:00:00Z", 0, 0);

        assertThat(allowance.daysLeft()).isEqualTo(8);
        assertThat(allowance.periodStart()).isEqualTo(Instant.parse("2026-09-30T22:00:00Z")); // 1 Oct, Warsaw
        assertThat(allowance.dayAllowance()).isEqualTo(7);
        assertThat(allowance.available()).isEqualTo(7);
    }

    @Test
    void todaysRunsSpendTodaysShareOnlyOnce() {
        assertThat(at("2026-10-24T12:00:00Z", 0, 3).dayAllowance()).isEqualTo(4);
        assertThat(at("2026-10-24T12:00:00Z", 0, 9).dayAllowance()).isZero();
    }

    @Test
    void unspentDaysRollForwardAndTheLastDayGetsTheRest() {
        // 30 Oct: 10 spent so far, 2 days left → (60 − 10) / 2
        assertThat(at("2026-10-30T06:00:00Z", 10, 0).dayAllowance()).isEqualTo(25);
        // 31 Oct, the period's last day
        assertThat(at("2026-10-31T06:00:00Z", 40, 0).dayAllowance()).isEqualTo(20);
        // overspent: nothing, never negative
        assertThat(at("2026-10-31T06:00:00Z", 70, 0).dayAllowance()).isZero();
    }

    @Test
    void theSubscriptionPeriodWinsWhileNowIsInsideIt() {
        when(tracker.latest()).thenReturn(Optional.of(quota(80, "2026-10-05T19:44:34Z", "2026-11-05T10:00:00Z")));

        SyncBudget.Allowance allowance = at("2026-10-24T06:00:00Z", 0, 0);

        assertThat(allowance.periodStart()).isEqualTo(Instant.parse("2026-10-05T19:44:34Z"));
        assertThat(allowance.daysLeft()).isEqualTo(13); // 24 Oct … 5 Nov
        assertThat(allowance.dayAllowance()).isEqualTo(4); // 60 / 13
    }

    /** Stage 0 saw a period end in the past while the counter kept counting: fall back to the calendar month. */
    @Test
    void aStalePeriodFallsBackToTheCalendarMonth() {
        when(tracker.latest()).thenReturn(Optional.of(quota(70, "2026-10-05T19:44:34Z", "2026-10-24T08:03:06Z")));

        assertThat(at("2026-10-24T12:00:00Z", 0, 0).daysLeft()).isEqualTo(8);
    }

    @Test
    void neverBelowKeepRemaining() {
        when(tracker.latest()).thenReturn(Optional.of(quota(12, null, null)));
        SyncBudget.Allowance allowance = at("2026-10-24T06:00:00Z", 0, 0);
        assertThat(allowance.quotaCap()).isEqualTo(2);
        assertThat(allowance.available()).isEqualTo(2);

        when(tracker.latest()).thenReturn(Optional.of(quota(5, null, null)));
        assertThat(at("2026-10-24T06:00:00Z", 0, 0).available()).isZero();
    }

    /** a blocking snapshot older than an hour caps nothing: the run's first request rechecks it */
    @Test
    void anOldBlockingSnapshotAllowsAProbe() {
        when(tracker.latest()).thenReturn(Optional.of(quota(5, null, null))); // observed 08:00Z

        SyncBudget.Allowance recent = at("2026-10-24T08:30:00Z", 0, 0);
        assertThat(recent.quotaProbe()).isFalse();
        assertThat(recent.manualMax()).isZero();

        SyncBudget.Allowance old = at("2026-10-24T09:30:00Z", 0, 0);
        assertThat(old.quotaProbe()).isTrue();
        assertThat(old.quotaCap()).isZero();
        assertThat(old.available()).isEqualTo(7);
        assertThat(old.manualMax()).isEqualTo(60);
    }
}
