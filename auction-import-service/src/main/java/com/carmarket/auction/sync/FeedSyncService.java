package com.carmarket.auction.sync;

import com.carmarket.auction.client.ApibaraChangesPage;
import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraCursorExpiredException;
import com.carmarket.auction.client.ApibaraQuota;
import com.carmarket.auction.client.ApibaraQuotaExceededException;
import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.client.ApibaraRejectedException;
import com.carmarket.auction.client.ApibaraUnavailableException;
import com.carmarket.auction.client.ApibaraVehiclesPage;
import com.carmarket.auction.config.ApibaraSyncProperties;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.entity.SyncStream;
import com.carmarket.auction.parser.ApibaraAuctionParser;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.repository.SyncStreamRepository;
import com.carmarket.auction.service.AuctionLotService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One run of the Apibara feed sync (docs/tasks/auction-feed-rollout.md, Stage B), within today's allowance
 * ({@link SyncBudget}) and one at a time across instances ({@link SyncLock}):
 * <ol>
 *   <li>outcomes: GET /changes?terminal=1 from the stored sequence until caught up, applied to the lots we store;
 *   the sequence moves only after the page's transaction committed; the first run starts at the newest event;</li>
 *   <li>the auction days today … today+days-ahead, closest first, each when its last complete pass is older than
 *   its refresh-hours: a cursor scan of GET /vehicles for that day, resumed in the next run if the allowance ran
 *   out. The order isn't stable (lots move when re-crawled), so a pass can miss a few; the next pass gets them.</li>
 * </ol>
 * Every run is a sync_runs row (parser "FeedSync"), with the requests it spent and why it stopped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedSyncService {

    public static final String PARSER = "FeedSync";

    public enum StopReason { DONE, BUDGET, QUOTA_EXHAUSTED, OUTSIDE_HOURS, DISABLED, LOCKED, ERROR }

    private final ApibaraClient client;
    private final ApibaraAuctionParser mapper;
    private final AuctionLotService lotService;
    private final SyncRunRepository runs;
    private final SyncStreamRepository streams;
    private final SyncBudget budget;
    private final ApibaraSyncProperties props;
    private final ParserTargetsConfig apibara;
    private final ApibaraQuotaTracker quotaTracker;
    private final SyncLock lock;
    private final Clock clock;

    /** One auction day for the admin status: its last complete pass, the day's size and the current pass. */
    public record DayStatus(String day, Instant lastCompletePassAt, Integer lastTotal, Integer pagesDone,
                            boolean passInProgress, Integer lotsLeft) {
    }

    /**
     * GET /api/auctions/admin/status → feed: today's allowance and spend, each day, how far the outcomes lag
     * behind Apibara's newest event, and a one-line verdict.
     */
    public record FeedStatus(boolean enabled, String schedule, SyncBudget.Allowance allowance, List<DayStatus> days,
                             Long outcomesSequence, Integer outcomesLag, Instant outcomesCheckedAt, String verdict) {
    }

    public FeedStatus status() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(props.getZone()).toLocalDate();
        List<DayStatus> days = new ArrayList<>();
        int stale = 0;
        for (int offset = 0; offset <= props.getDaysAhead(); offset++) {
            String key = dayKey(today.plusDays(offset));
            SyncStream stream = streams.findById(key).orElse(null);
            Duration refresh = Duration.ofHours(props.getRefreshHours().forDay(offset));
            boolean fresh = stream != null && stream.getCursor() == null && stream.getLastFullAt() != null
                && !stream.getLastFullAt().isBefore(now.minus(refresh));
            if (!fresh) stale++;
            days.add(stream == null
                ? new DayStatus(key.substring(SyncStream.DAY_PREFIX.length()), null, null, null, false, null)
                : new DayStatus(key.substring(SyncStream.DAY_PREFIX.length()), stream.getLastFullAt(), stream.getLastTotal(),
                    stream.getPagesDone(), stream.getCursor() != null, stream.getBacklogEstimate()));
        }
        SyncStream outcomes = streams.findById(SyncStream.OUTCOMES).orElse(null);
        String verdict = stale == 0
            ? (props.getDaysAhead() == 0 ? "Dziś aktualne" : props.getDaysAhead() == 1 ? "Dziś i jutro aktualne"
                : "Aktualne na " + (props.getDaysAhead() + 1) + " dni")
            : "Budżet nie wystarcza na " + stale + (stale == 1 ? " dzień" : " dni");
        String hours = props.activeFrom() + "–" + props.activeTo();
        String schedule = props.getRunEveryMinutes() > 0
            ? "od " + props.getDailyStart() + " co " + props.getRunEveryMinutes() + " min (" + hours + ")"
            : "raz dziennie o " + props.getDailyStart() + " (" + hours + ")";
        return new FeedStatus(props.isEnabled(), schedule, budget.today(), days,
            outcomes == null ? null : outcomes.getSequence(), outcomes == null ? null : outcomes.getBacklogEstimate(),
            outcomes == null ? null : outcomes.getLastRunAt(), verdict);
    }

    /** A scheduled run: today's allowance. */
    public SyncRun run(String trigger) {
        return run(trigger, null);
    }

    /**
     * Runs the sync now. The caller decides whether it's due: apibara.sync.enabled only gates the schedule
     * (LotSyncScheduler), an admin's manual run works without it. The budget is always checked here.
     *
     * @param maxRequests an admin's manual limit, up to what the period still allows ({@link SyncBudget.Allowance#manualMax()},
     *                    so it may exceed today's share); null = today's allowance
     */
    public SyncRun run(String trigger, Integer maxRequests) {
        SyncRun run = SyncRun.start(PARSER, trigger);
        run.setStartedAt(clock.instant());
        run.setMode("FEED");
        if (!client.isConfigured()) {
            return finish(run, StopReason.ERROR, "apibara.api-key is not set", 0);
        }
        Instant retryNotBefore = quotaTracker.retryNotBefore();
        if (retryNotBefore != null && clock.instant().isBefore(retryNotBefore)) {
            return finish(run, StopReason.QUOTA_EXHAUSTED, "Apibara asked to wait until " + retryNotBefore, 0);
        }
        Optional<SyncLock.Held> held = lock.tryAcquire();
        if (held.isEmpty()) {
            return finish(run, StopReason.LOCKED, "Another sync is running", 0);
        }
        try (SyncLock.Held ignored = held.get()) {
            runs.save(run); // visible at once: a restart in the meantime must not start today's run again
            long requestsBefore = quotaTracker.requestsSent();
            SyncBudget.Allowance allowance = budget.today();
            Work work = new Work(maxRequests == null ? allowance.available() : Math.min(maxRequests, allowance.manualMax()));
            StopReason reason;
            String error = null;
            try {
                if (outcomes(work, run) && days(work, run)) {
                    reason = StopReason.DONE;
                } else {
                    reason = work.stop;
                }
            } catch (ApibaraQuotaExceededException e) {
                log.warn("Feed sync stopped: {}", e.getMessage());
                reason = StopReason.QUOTA_EXHAUSTED;
            } catch (ApibaraUnavailableException e) {
                log.error("Feed sync failed: {}", e.getMessage());
                reason = StopReason.ERROR;
                error = e.getMessage();
            } catch (RuntimeException e) {
                log.error("Feed sync failed", e);
                reason = StopReason.ERROR;
                error = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            }
            run.setStreamsWithBacklog((int) streams.findAll().stream().filter(SyncStream::isBacklog).count());
            return finish(run, reason, error, (int) (quotaTracker.requestsSent() - requestsBefore));
        }
    }

    /** Requests this run may still send, and why it stopped when it can't. */
    private final class Work {
        private final int allowed;
        private int taken;
        private StopReason stop;

        private Work(int allowed) {
            this.allowed = allowed;
        }

        /** Reserves one request; false when the allowance or the quota (remaining − keep-remaining) is used up. */
        boolean take() {
            if (taken >= allowed) {
                stop = StopReason.BUDGET;
                return false;
            }
            ApibaraQuota quota = quotaTracker.latest().orElse(null);
            if (quota != null && quota.remaining() != null && quota.remaining() <= props.getKeepRemaining()) {
                stop = StopReason.QUOTA_EXHAUSTED;
                return false;
            }
            taken++;
            return true;
        }
    }

    // ── 1. outcomes ──

    /** False when the run must stop here (allowance or quota used up). */
    private boolean outcomes(Work work, SyncRun run) {
        SyncStream stream = streams.findById(SyncStream.OUTCOMES).orElseGet(() -> new SyncStream(SyncStream.OUTCOMES));
        if (stream.getSequence() == null) {
            // the first run starts at the newest event: replaying 14 days would cost hundreds of requests
            // for the few lots stored before
            if (!work.take()) return false;
            ApibaraChangesPage first = client.terminalChangesPage(0, 1);
            stream.setSequence(first.latestAvailableSequence() != null ? first.latestAvailableSequence() : first.nextCursor());
            stream.setLastRunAt(clock.instant());
            stream.setBacklogEstimate(0);
            streams.save(stream);
            log.info("Outcomes start at sequence {}", stream.getSequence());
            run.setStreamsDone(count(run.getStreamsDone()) + 1);
            return true;
        }
        int applied = 0;
        while (true) {
            if (!work.take()) {
                stream.setBacklog(true);
                streams.save(stream);
                return false;
            }
            ApibaraChangesPage page;
            try {
                page = client.terminalChangesPage(stream.getSequence(), props.getChangesLimit());
            } catch (ApibaraCursorExpiredException e) {
                if (e.getLatestAvailableSequence() == null) throw e;
                log.warn("Outcomes sequence {} expired; jumping to {}, the expiry job covers the gap",
                    stream.getSequence(), e.getLatestAvailableSequence());
                stream.setSequence(e.getLatestAvailableSequence());
                streams.save(stream);
                continue;
            }
            List<LotOutcome> outcomes = new ArrayList<>();
            for (JsonNode event : page.events()) {
                ChangeEventMapper.toOutcome(event).ifPresent(outcomes::add);
            }
            applied += lotService.applyOutcomes(outcomes); // commits; only then does the sequence move
            stream.setSequence(page.nextCursor());
            stream.setLastRunAt(clock.instant());
            stream.setBacklog(page.hasMore());
            stream.setBacklogEstimate(page.latestAvailableSequence() == null ? null
                : (int) Math.min(Integer.MAX_VALUE, Math.max(0, page.latestAvailableSequence() - page.nextCursor())));
            if (!page.hasMore()) {
                stream.setLastFullAt(clock.instant());
                streams.save(stream);
                log.info("Outcomes caught up at sequence {}: {} lots changed", stream.getSequence(), applied);
                run.setLotsUpdated(count(run.getLotsUpdated()) + applied);
                run.setStreamsDone(count(run.getStreamsDone()) + 1);
                return true;
            }
            streams.save(stream);
        }
    }

    // ── 2. auction days ──

    /** False when the run must stop here (allowance or quota used up). */
    private boolean days(Work work, SyncRun run) {
        LocalDate today = clock.instant().atZone(props.getZone()).toLocalDate();
        streams.deleteDaysBefore(dayKey(today.minusDays(1)));
        for (int offset = 0; offset <= props.getDaysAhead(); offset++) {
            LocalDate day = today.plusDays(offset);
            SyncStream stream = streams.findById(dayKey(day)).orElseGet(() -> new SyncStream(dayKey(day)));
            Duration refresh = Duration.ofHours(props.getRefreshHours().forDay(offset));
            boolean due = stream.getCursor() != null || stream.getLastFullAt() == null
                || stream.getLastFullAt().isBefore(clock.instant().minus(refresh));
            if (due && !pass(work, run, stream, day)) {
                return false;
            }
        }
        return true;
    }

    /** Reads one auction day page by page; false when the allowance ran out before the pass was complete. */
    private boolean pass(Work work, SyncRun run, SyncStream stream, LocalDate day) {
        int perPage = client.effectivePerPage(apibara.getPerPage());
        while (true) {
            if (!work.take()) {
                stream.setBacklog(true);
                streams.save(stream);
                return false;
            }
            String cursor = stream.getCursor();
            if (cursor == null) {
                stream.setWatermark(clock.instant()); // a new pass starts
                stream.setPagesDone(0);
            }
            ApibaraVehiclesPage page;
            try {
                page = client.searchVehiclesPage(dayParams(day, cursor, perPage));
            } catch (ApibaraRejectedException e) {
                if (cursor == null) throw e;
                log.warn("Apibara rejected the cursor of {} (HTTP {}); starting the pass again", stream.getStreamKey(), e.getStatus());
                stream.setCursor(null);
                streams.save(stream);
                continue;
            }
            if (cursor == null && page.total() != null) {
                stream.setLastTotal(page.total());
            }
            ApibaraAuctionParser.MappedPage mapped = mapper.map(page.vehicles());
            if (!mapped.lots().isEmpty()) {
                AuctionLotService.SyncCounts counts = lotService.saveOrUpdateLots(mapped.lots()); // commits the page
                run.setLotsAdded(count(run.getLotsAdded()) + counts.added());
                run.setLotsUpdated(count(run.getLotsUpdated()) + counts.updated());
            }
            run.setLotsFetched(count(run.getLotsFetched()) + page.vehicles().size());
            run.setSkippedType(count(run.getSkippedType()) + mapped.skippedType());
            run.setSkippedCondition(count(run.getSkippedCondition()) + mapped.skippedCondition());
            run.setSkippedTitle(count(run.getSkippedTitle()) + mapped.skippedTitle());
            if (page.vehicles().size() > mapped.lots().size()) {
                log.info("{}: kept {} of {} (skipped_type={}, skipped_condition={}, skipped_title={})", stream.getStreamKey(),
                    mapped.lots().size(), page.vehicles().size(), mapped.skippedType(), mapped.skippedCondition(), mapped.skippedTitle());
            }
            stream.setPagesDone(count(stream.getPagesDone()) + 1);
            stream.setLastRunAt(clock.instant());
            stream.setCursor(page.nextCursor());
            if (page.nextCursor() == null) {
                stream.setLastFullAt(stream.getWatermark());
                stream.setBacklog(false);
                stream.setBacklogEstimate(0);
                streams.save(stream);
                log.info("{}: pass complete, {} pages, total {}", stream.getStreamKey(), stream.getPagesDone(), stream.getLastTotal());
                run.setStreamsDone(count(run.getStreamsDone()) + 1);
                return true;
            }
            stream.setBacklog(true);
            if (stream.getLastTotal() != null) {
                stream.setBacklogEstimate(Math.max(0, stream.getLastTotal() - stream.getPagesDone() * perPage));
            }
            streams.save(stream);
        }
    }

    private MultiValueMap<String, String> dayParams(LocalDate day, String cursor, int perPage) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("auction_type", "0"); // both platforms in one request
        apibara.getVehicleTypes().getInclude().forEach(type -> params.add("type[]", type));
        params.add("upcoming", "only");
        if (apibara.getFilters().isRunAndDriveOnly()) {
            params.add("run_cond", "RUNS AND DRIVES"); // server-side; titles can only be filtered on our side
        }
        params.add("auction_date_from", day.toString());
        params.add("auction_date_to", day.toString());
        if (props.getYearFrom() != null) {
            params.add("year_from", String.valueOf(props.getYearFrom()));
        }
        params.add("per_page", String.valueOf(perPage));
        if (cursor != null) {
            params.add("cursor", cursor);
        } else {
            params.add("include_total", "true"); // the day's size, once per pass
        }
        return params;
    }

    static String dayKey(LocalDate day) {
        return SyncStream.DAY_PREFIX + day;
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    private SyncRun finish(SyncRun run, StopReason reason, String error, int requests) {
        run.setStopReason(reason.name());
        run.setApiRequests(requests);
        if (error != null) {
            run.setError(error.length() > 1000 ? error.substring(0, 1000) : error);
        }
        quotaTracker.latest()
            .filter(q -> q.observedAt() != null && !q.observedAt().isBefore(run.getStartedAt()))
            .ifPresent(q -> {
                run.setQuotaLimit(q.limit());
                run.setQuotaUsed(q.used());
                run.setQuotaRemaining(q.remaining());
                run.setQuotaPeriodStart(q.periodStart());
                run.setQuotaPeriodEnd(q.periodEnd());
                run.setQuotaObservedAt(q.observedAt());
            });
        run.setFinishedAt(clock.instant());
        log.info("Feed sync ({}) stopped: {}, {} requests, {} lots fetched, skipped_type={}, skipped_condition={}, skipped_title={}",
            run.getTrigger(), reason, requests, count(run.getLotsFetched()), count(run.getSkippedType()),
            count(run.getSkippedCondition()), count(run.getSkippedTitle()));
        return runs.save(run);
    }
}
