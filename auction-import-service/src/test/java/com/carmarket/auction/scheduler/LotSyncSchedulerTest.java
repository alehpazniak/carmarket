package com.carmarket.auction.scheduler;

import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.parser.AuctionParser;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.service.AuctionLotService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotSyncSchedulerTest {

    private final SyncRunRepository runs = mock(SyncRunRepository.class);
    private final AuctionLotService lotService = mock(AuctionLotService.class);

    {
        when(runs.save(any(SyncRun.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void everyParserRunIsRecordedWithCountsOrTheError() throws Exception {
        AuctionParser ok = mock(AuctionParser.class);
        AuctionParser broken = mock(AuctionParser.class);
        List<AuctionLot> page1 = List.of(new AuctionLot(), new AuctionLot());
        List<AuctionLot> page2 = List.of(new AuctionLot());
        when(ok.parseLiveLotPages()).thenReturn(List.of(page1, page2));
        when(lotService.saveOrUpdateLots(page1)).thenReturn(new AuctionLotService.SyncCounts(1, 1));
        when(lotService.saveOrUpdateLots(page2)).thenReturn(new AuctionLotService.SyncCounts(1, 0));
        when(broken.parseLiveLotPages()).thenThrow(new IllegalStateException("quota exceeded"));

        new LotSyncScheduler(List.of(ok, broken), lotService, runs).syncAllSources();

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

    @Test
    void onlyOneSyncAtATime() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AuctionParser slow = mock(AuctionParser.class);
        when(slow.parseLiveLotPages()).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        when(lotService.saveOrUpdateLots(any())).thenReturn(new AuctionLotService.SyncCounts(0, 0));
        LotSyncScheduler scheduler = new LotSyncScheduler(List.of(slow), lotService, runs);

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
