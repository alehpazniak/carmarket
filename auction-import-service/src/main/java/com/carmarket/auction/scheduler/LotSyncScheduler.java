package com.carmarket.auction.scheduler;

import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.config.ApibaraSyncProperties;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.sync.FeedSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the feed sync (FeedSyncService) on its schedule, or when an admin asks (POST /api/auctions/admin/sync).
 * <p>Scheduled runs: slots at apibara.sync.daily-start, then every run-every-minutes (0 = only that one) until the
 * end of the active hours. A minute-by-minute check starts a slot's run once now is past the slot, still inside the
 * active hours, and no SCHEDULED run was recorded since the slot. So a restart the same day doesn't run it again,
 * and a start after 08:00 runs it only if today's run hasn't happened yet. Nothing starts while disabled or while
 * Apibara's Retry-After lasts (that doesn't use up the slot). apibara.sync.enabled gates only this schedule: a manual
 * run ignores it, the slots and the active hours; the budget, keep-remaining, 429 and the lock still apply
 * (FeedSyncService).
 */
@Slf4j
@Component
public class LotSyncScheduler {

    static final String SCHEDULED = "SCHEDULED";

    private final FeedSyncService feedSync;
    private final SyncRunRepository runs;
    private final ApibaraSyncProperties props;
    private final ApibaraQuotaTracker quotaTracker;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService manualRuns = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "manual-lot-sync");
        t.setDaemon(true);
        return t;
    });

    public LotSyncScheduler(FeedSyncService feedSync, SyncRunRepository runs, ApibaraSyncProperties props,
                            ApibaraQuotaTracker quotaTracker, Clock clock) {
        this.feedSync = feedSync;
        this.runs = runs;
        this.props = props;
        this.quotaTracker = quotaTracker;
        this.clock = clock;
    }

    /** Cheap when nothing is due: one indexed EXISTS on sync_runs. */
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        if (!props.isEnabled()) {
            return;
        }
        Optional<Instant> slot = dueSlot();
        if (slot.isEmpty() || runs.existsByParserAndTriggerAndStartedAtGreaterThanEqual(FeedSyncService.PARSER, SCHEDULED, slot.get())) {
            return;
        }
        Instant retryNotBefore = quotaTracker.retryNotBefore();
        if (retryNotBefore != null && clock.instant().isBefore(retryNotBefore)) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            feedSync.run(SCHEDULED);
        } finally {
            running.set(false);
        }
    }

    /** The latest slot of today that has begun, if now is inside the active hours. */
    Optional<Instant> dueSlot() {
        ZonedDateTime now = clock.instant().atZone(props.getZone());
        LocalTime time = now.toLocalTime();
        if (!props.isActive(time) || time.isBefore(props.getDailyStart())) {
            return Optional.empty();
        }
        LocalTime slot = props.getDailyStart();
        int every = props.getRunEveryMinutes();
        if (every > 0) {
            long minutesSinceStart = java.time.Duration.between(props.getDailyStart(), time).toMinutes();
            slot = props.getDailyStart().plusMinutes(minutesSinceStart / every * every);
        }
        return Optional.of(now.with(slot).withSecond(0).withNano(0).toInstant());
    }

    /**
     * When the next scheduled run starts: now (within a minute) if today's slot is due and hasn't run, else the next
     * slot today inside the active hours, else tomorrow's first. Empty while the schedule is disabled.
     */
    public Optional<Instant> nextScheduledRun() {
        if (!props.isEnabled()) {
            return Optional.empty();
        }
        Optional<Instant> due = dueSlot();
        if (due.isPresent() && !runs.existsByParserAndTriggerAndStartedAtGreaterThanEqual(FeedSyncService.PARSER, SCHEDULED, due.get())) {
            return Optional.of(clock.instant());
        }
        ZonedDateTime now = clock.instant().atZone(props.getZone());
        LocalTime time = now.toLocalTime();
        LocalTime start = props.getDailyStart();
        int every = props.getRunEveryMinutes();
        LocalTime next = null;
        if (time.isBefore(start)) {
            next = start;
        } else if (every > 0) {
            long minutes = (java.time.Duration.between(start, time).toMinutes() / every + 1) * every;
            // a slot past midnight would wrap around to the morning: tomorrow's first slot instead
            if (minutes < java.time.Duration.between(start, LocalTime.MAX).toMinutes()) next = start.plusMinutes(minutes);
        }
        if (next != null && props.isActive(next)) {
            return Optional.of(now.with(next).withSecond(0).withNano(0).toInstant());
        }
        return Optional.of(now.toLocalDate().plusDays(1).atTime(start).atZone(props.getZone()).toInstant());
    }

    /** Starts today's allowance in the background; false when a sync is already running. */
    public boolean triggerManualSync() {
        return triggerManualSync(null);
    }

    /**
     * Starts a sync in the background, also while the schedule is disabled; false when one is already running (the
     * caller answers 409). maxRequests: the admin's limit (validated by the caller), null = today's allowance.
     */
    public boolean triggerManualSync(Integer maxRequests) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            manualRuns.execute(() -> {
                try {
                    feedSync.run("MANUAL", maxRequests);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }
}
