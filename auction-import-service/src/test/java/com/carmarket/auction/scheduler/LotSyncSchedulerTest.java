package com.carmarket.auction.scheduler;

import com.carmarket.auction.client.ApibaraQuota;
import com.carmarket.auction.client.ApibaraQuotaExceededException;
import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.parser.AuctionParser;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.service.AuctionLotService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotSyncSchedulerTest {

    private final SyncRunRepository runs = mock(SyncRunRepository.class);
    private final AuctionLotService lotService = mock(AuctionLotService.class);
    private final ApibaraQuotaTracker tracker = mock(ApibaraQuotaTracker.class);

    {
        when(runs.save(any(SyncRun.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void everyParserRunIsRecordedWithCountsOrTheError() throws Exception {
        AuctionParser ok = mock(AuctionParser.class);
        AuctionParser broken = mock(AuctionParser.class);
        List<AuctionLot> page1 = List.of(new AuctionLot(), new AuctionLot());
        List<AuctionLot> page2 = List.of(new AuctionLot());
        handsOver(ok, page1, page2);
        when(lotService.saveOrUpdateLots(page1)).thenReturn(new AuctionLotService.SyncCounts(1, 1));
        when(lotService.saveOrUpdateLots(page2)).thenReturn(new AuctionLotService.SyncCounts(1, 0));
        doAnswer(inv -> {
            throw new IllegalStateException("quota exceeded");
        }).when(broken).forEachLiveLotPage(any());
        when(tracker.latest()).thenReturn(Optional.empty());

        new LotSyncScheduler(List.of(ok, broken), lotService, runs, tracker).syncAllSources();

        ArgumentCaptor<SyncRun> saved = ArgumentCaptor.forClass(SyncRun.class);
        verify(runs, atLeastOnce()).save(saved.capture());
        List<SyncRun> finished = saved.getAllValues().stream().filter(r -> r.getFinishedAt() != null).distinct().toList();
        assertThat(finished).hasSize(2);
        assertThat(finished.get(0).getLotsFetched()).isEqualTo(3);
        assertThat(finished.get(0).getLotsAdded()).isEqualTo(2);
        assertThat(finished.get(0).getLotsUpdated()).isEqualTo(1);
        assertThat(finished.get(0).getTrigger()).isEqualTo("SCHEDULED");
        assertThat(finished.get(1).getError()).isEqualTo("IllegalStateException: quota exceeded");
        // each response page is its own call, i.e. its own transaction
        verify(lotService).saveOrUpdateLots(page1);
        verify(lotService).saveOrUpdateLots(page2);
    }

    @SafeVarargs
    private static void handsOver(AuctionParser parser, List<AuctionLot>... pages) {
        doAnswer(inv -> {
            Consumer<List<AuctionLot>> consumer = inv.getArgument(0);
            for (List<AuctionLot> page : pages) consumer.accept(page);
            return null;
        }).when(parser).forEachLiveLotPage(any());
    }

    private SyncRun lastSaved() {
        ArgumentCaptor<SyncRun> saved = ArgumentCaptor.forClass(SyncRun.class);
        verify(runs, atLeastOnce()).save(saved.capture());
        return saved.getAllValues().get(saved.getAllValues().size() - 1);
    }

    @Test
    void aQuotaErrorKeepsTheSavedPagesAndRecordsRequestsAndQuota() {
        AuctionParser apibara = mock(AuctionParser.class);
        List<AuctionLot> page = List.of(new AuctionLot());
        ApibaraQuota quota = new ApibaraQuota(100, 100, 0, null, null, null, null, Instant.now().plusSeconds(1));
        doAnswer(inv -> {
            Consumer<List<AuctionLot>> consumer = inv.getArgument(0);
            consumer.accept(page);
            throw new ApibaraQuotaExceededException(Duration.ofMinutes(5), quota);
        }).when(apibara).forEachLiveLotPage(any());
        when(lotService.saveOrUpdateLots(page)).thenReturn(new AuctionLotService.SyncCounts(1, 0));
        when(tracker.requestsSent()).thenReturn(7L, 9L); // before and after the run
        when(tracker.latest()).thenReturn(Optional.of(quota));

        new LotSyncScheduler(List.of(apibara), lotService, runs, tracker).syncAllSources();

        SyncRun run = lastSaved();
        assertThat(run.getLotsAdded()).isEqualTo(1);
        assertThat(run.getLotsFetched()).isEqualTo(1);
        assertThat(run.getError()).isEqualTo("ApibaraQuotaExceededException: Apibara quota exhausted, retry after 300 s");
        assertThat(run.getApiRequests()).isEqualTo(2);
        assertThat(run.getQuotaRemaining()).isZero();
        assertThat(run.getQuotaLimit()).isEqualTo(100);
        assertThat(run.getQuotaObservedAt()).isEqualTo(quota.observedAt());
    }

    @Test
    void aQuotaSnapshotFromBeforeTheRunIsNotStoredAgain() {
        AuctionParser parser = mock(AuctionParser.class);
        when(tracker.latest()).thenReturn(Optional.of(
            new ApibaraQuota(100, 50, 50, null, null, null, null, Instant.now().minusSeconds(3600))));

        new LotSyncScheduler(List.of(parser), lotService, runs, tracker).syncAllSources();

        SyncRun run = lastSaved();
        assertThat(run.getQuotaRemaining()).isNull();
        assertThat(run.getApiRequests()).isZero();
    }

    @Test
    void onlyOneSyncAtATime() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AuctionParser slow = mock(AuctionParser.class);
        doAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(slow).forEachLiveLotPage(any());
        when(tracker.latest()).thenReturn(Optional.empty());
        when(lotService.saveOrUpdateLots(any())).thenReturn(new AuctionLotService.SyncCounts(0, 0));
        LotSyncScheduler scheduler = new LotSyncScheduler(List.of(slow), lotService, runs, tracker);

        assertThat(scheduler.triggerManualSync()).isTrue();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.isRunning()).isTrue();
        assertThat(scheduler.triggerManualSync()).isFalse();
        scheduler.syncAllSources(); // the scheduled run is skipped, not queued

        release.countDown();
        for (int i = 0; i < 50 && scheduler.isRunning(); i++) Thread.sleep(20);
        assertThat(scheduler.isRunning()).isFalse();
        assertThat(scheduler.triggerManualSync()).isTrue();
    }
}
