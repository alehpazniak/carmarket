package com.carmarket.auction.scheduler;

import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.parser.AuctionParser;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.service.AuctionLotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls live lots from every parser, on a schedule or when an admin asks (POST /api/auctions/admin/sync).
 * Only one sync runs at a time; each parser run is recorded in sync_runs for the admin status page.
 */
@Slf4j
@Component
public class LotSyncScheduler {

    private final List<AuctionParser> parsers;
    private final AuctionLotService lotService;
    private final SyncRunRepository runs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService manualRuns = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "manual-lot-sync");
        t.setDaemon(true);
        return t;
    });

    public LotSyncScheduler(List<AuctionParser> parsers, AuctionLotService lotService, SyncRunRepository runs) {
        this.parsers = parsers;
        this.lotService = lotService;
        this.runs = runs;
    }

    @Scheduled(fixedDelayString = "${parser.sync.interval:300000}") // default 5 min
    public void syncAllSources() {
        if (!running.compareAndSet(false, true)) {
            log.info("Scheduled lot sync skipped: a sync is already running");
            return;
        }
        try {
            runAll("SCHEDULED");
        } finally {
            running.set(false);
        }
    }

    /** Starts a sync in the background; false when one is already running (the caller answers 409). */
    public boolean triggerManualSync() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            manualRuns.execute(() -> {
                try {
                    runAll("MANUAL");
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

    void runAll(String trigger) {
        for (AuctionParser parser : parsers) {
            String name = parser.getClass().getSimpleName();
            SyncRun run = runs.save(SyncRun.start(name, trigger));
            try {
                log.info("Starting {} sync for {}", trigger.toLowerCase(), name);
                List<AuctionLot> lots = parser.parseLiveLots();
                AuctionLotService.SyncCounts counts = lotService.saveOrUpdateLots(lots);
                run.setLotsFetched(lots.size());
                run.setLotsAdded(counts.added());
                run.setLotsUpdated(counts.updated());
                log.info("Synced {} lots from {} ({} new, {} updated)", lots.size(), name, counts.added(), counts.updated());
            } catch (Exception e) {
                log.error("Sync failed for {}", name, e);
                String message = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                run.setError(message.length() > 1000 ? message.substring(0, 1000) : message);
            }
            run.setFinishedAt(Instant.now());
            runs.save(run);
        }
    }
}
