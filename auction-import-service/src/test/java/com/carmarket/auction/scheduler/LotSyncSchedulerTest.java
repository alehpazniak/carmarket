package com.carmarket.auction.scheduler;

import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.config.ApibaraSyncProperties;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.sync.FeedSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** One scheduled run a day at 08:00 Warsaw (free plan); restarts don't repeat it, a late start catches it up. */
class LotSyncSchedulerTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final FeedSyncService feedSync = mock(FeedSyncService.class);
    private final SyncRunRepository runs = mock(SyncRunRepository.class);
    private final ApibaraQuotaTracker tracker = mock(ApibaraQuotaTracker.class);
    private final ApibaraSyncProperties props = new ApibaraSyncProperties();

    @BeforeEach
    void enabled() {
        props.setEnabled(true);
        when(feedSync.run(anyString())).thenReturn(new SyncRun());
    }

    private LotSyncScheduler at(String warsawTime) {
        return new LotSyncScheduler(feedSync, runs, props, tracker, Clock.fixed(warsaw(warsawTime), WARSAW));
    }

    /** 24 Oct 2026 is still summer time in Warsaw: UTC+2 */
    private static Instant warsaw(String time) {
        return Instant.parse("2026-10-24T" + time + ":00Z").minusSeconds(2 * 3600);
    }

    @Test
    void theDailyRunStartsFromEightOnce() {
        at("07:59").tick();
        verify(feedSync, never()).run(anyString());

        at("08:00").tick();
        verify(feedSync).run("SCHEDULED");
        verify(runs).existsByParserAndTriggerAndStartedAtGreaterThanEqual(FeedSyncService.PARSER, "SCHEDULED", warsaw("08:00"));
    }

    @Test
    void aRestartTheSameDayDoesntRunItAgain() {
        when(runs.existsByParserAndTriggerAndStartedAtGreaterThanEqual(FeedSyncService.PARSER, "SCHEDULED", warsaw("08:00")))
            .thenReturn(true);

        at("08:05").tick();
        at("15:30").tick();

        verify(feedSync, never()).run(anyString());
    }

    @Test
    void aStartAfterEightCatchesUpTodaysRun() {
        at("13:17").tick();

        verify(feedSync).run("SCHEDULED");
    }

    @Test
    void nothingOutsideTheActiveHoursOrWhileDisabledOrWaitingForRetryAfter() {
        at("23:30").tick();

        props.setEnabled(false);
        at("09:00").tick();

        props.setEnabled(true);
        when(tracker.retryNotBefore()).thenReturn(warsaw("09:30"));
        at("09:00").tick();

        verify(feedSync, never()).run(anyString());
    }

    @Test
    void withAnIntervalTheSlotIsTheLatestOneStarted() {
        props.setRunEveryMinutes(30);

        at("10:47").tick();

        verify(runs).existsByParserAndTriggerAndStartedAtGreaterThanEqual(FeedSyncService.PARSER, "SCHEDULED", warsaw("10:30"));
        verify(feedSync).run("SCHEDULED");
    }

    @Test
    void aDisabledSchedulerDoesntEvenAsk() {
        props.setEnabled(false);
        at("09:00").tick();
        verifyNoInteractions(runs);
    }

    @Test
    void manualRunsIgnoreTheScheduleAndOnlyOneRunsAtATime() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(feedSync.run(eq("MANUAL"), any())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new SyncRun();
        });
        props.setEnabled(false); // the schedule only
        LotSyncScheduler scheduler = at("23:30"); // outside the active hours

        assertThat(scheduler.triggerManualSync()).isTrue();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.isRunning()).isTrue();
        assertThat(scheduler.triggerManualSync()).isFalse();
        release.countDown();

        verify(feedSync, timeout(5000)).run("MANUAL", null);
        verify(feedSync, never()).run("SCHEDULED");
        for (int i = 0; i < 50 && scheduler.isRunning(); i++) {
            Thread.sleep(100);
        }
        assertThat(scheduler.isRunning()).isFalse();
        verify(runs, never()).save(any());
    }

    @Test
    void theNextScheduledRun() {
        assertThat(at("06:30").nextScheduledRun()).contains(warsaw("08:00"));
        // today's run is due and hasn't happened: it starts now
        assertThat(at("13:17").nextScheduledRun()).contains(warsaw("13:17"));

        when(runs.existsByParserAndTriggerAndStartedAtGreaterThanEqual(FeedSyncService.PARSER, "SCHEDULED", warsaw("08:00")))
            .thenReturn(true);
        // done for today: tomorrow 08:00 (25 Oct is winter time, UTC+1)
        assertThat(at("13:17").nextScheduledRun()).contains(Instant.parse("2026-10-25T07:00:00Z"));

        props.setRunEveryMinutes(30);
        when(runs.existsByParserAndTriggerAndStartedAtGreaterThanEqual(FeedSyncService.PARSER, "SCHEDULED", warsaw("13:00")))
            .thenReturn(true);
        assertThat(at("13:17").nextScheduledRun()).contains(warsaw("13:30"));

        props.setEnabled(false);
        assertThat(at("13:17").nextScheduledRun()).isEmpty();
    }
}
