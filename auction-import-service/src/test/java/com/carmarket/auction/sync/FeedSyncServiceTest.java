package com.carmarket.auction.sync;

import com.carmarket.auction.client.ApibaraChangesPage;
import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraCursorExpiredException;
import com.carmarket.auction.client.ApibaraQuota;
import com.carmarket.auction.client.ApibaraQuotaExceededException;
import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.client.ApibaraRejectedException;
import com.carmarket.auction.client.ApibaraResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.util.MultiValueMap;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedSyncServiceTest {

    /** Monday 26 Oct 2026, 08:00 in Warsaw (winter time, UTC+1) */
    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
    private static final String TODAY = "open:2026-10-26";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApibaraClient client = mock(ApibaraClient.class);
    private final AuctionLotService lotService = mock(AuctionLotService.class);
    private final SyncRunRepository runs = mock(SyncRunRepository.class);
    private final SyncStreamRepository streams = mock(SyncStreamRepository.class);
    private final SyncBudget budget = mock(SyncBudget.class);
    private final ApibaraQuotaTracker tracker = mock(ApibaraQuotaTracker.class);
    private final SyncLock lock = mock(SyncLock.class);
    private final ApibaraSyncProperties props = new ApibaraSyncProperties();
    private final ParserTargetsConfig apibara = new ParserTargetsConfig();
    private final Map<String, SyncStream> stored = new TreeMap<>();
    private FeedSyncService service;

    @BeforeEach
    void setUp() {
        props.setEnabled(true);
        props.setZone(ZoneId.of("Europe/Warsaw"));
        props.setKeepRemaining(10);
        apibara.getVehicleTypes().setInclude(List.of("AUTOMOBILE", "SUV", "PICKUP"));
        when(client.isConfigured()).thenReturn(true);
        when(client.effectivePerPage(anyInt())).thenReturn(20);
        when(lock.tryAcquire()).thenReturn(Optional.of((SyncLock.Held) () -> { }));
        when(tracker.latest()).thenReturn(Optional.empty());
        when(runs.save(any(SyncRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(streams.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<String>getArgument(0))));
        when(streams.save(any(SyncStream.class))).thenAnswer(inv -> {
            SyncStream s = inv.getArgument(0);
            stored.put(s.getStreamKey(), s);
            return s;
        });
        when(streams.findAll()).thenAnswer(inv -> List.copyOf(stored.values()));
        when(lotService.saveOrUpdateLots(any())).thenReturn(new AuctionLotService.SyncCounts(1, 0));
        allowance(10);
        service = new FeedSyncService(client, new ApibaraAuctionParser(apibara, objectMapper), lotService, runs, streams,
            budget, props, apibara, tracker, lock, Clock.fixed(NOW, props.getZone()));
    }

    private void allowance(int requests) {
        allowance(requests, 60);
    }

    /** today's allowance, and what the period still allows a manual run */
    private void allowance(int requests, int manualMax) {
        when(budget.today()).thenReturn(new SyncBudget.Allowance(null, null, 6, 0, 0, requests, null, requests, manualMax, manualMax));
    }

    private SyncStream outcomesAt(long sequence) {
        SyncStream s = new SyncStream(SyncStream.OUTCOMES);
        s.setSequence(sequence);
        stored.put(s.getStreamKey(), s);
        return s;
    }

    private JsonNode vehicle(String lotNumber) throws Exception {
        return objectMapper.readTree("""
            {"platform": "copart", "lot_number": "%s", "year": 2020, "make": "TOYOTA", "model": "RAV4", "type": "SUV", "condition": {"run_condition": {"value": "RUNS AND DRIVES"}},
             "auction": {"state": "open", "auction_at": "2026-10-26T15:00:00+00:00"}}""".formatted(lotNumber));
    }

    private static ApibaraVehiclesPage vehicles(List<JsonNode> lots, String nextCursor, Integer total) {
        return new ApibaraVehiclesPage(lots, new ApibaraResponse.Meta(nextCursor, 20), null, total);
    }

    private static ApibaraChangesPage changes(List<JsonNode> events, long next, boolean hasMore) {
        return new ApibaraChangesPage(events, next, hasMore, 900L, null);
    }

    private static MultiValueMap<String, String> firstPage() {
        return argThat(p -> p != null && p.getFirst("cursor") == null && "true".equals(p.getFirst("include_total")));
    }

    private static MultiValueMap<String, String> withCursor(String cursor) {
        return argThat(p -> p != null && cursor.equals(p.getFirst("cursor")));
    }

    @Test
    void outcomesFirstThenTodaysPass() throws Exception {
        outcomesAt(100);
        JsonNode sold = objectMapper.readTree("""
            {"event": "lot.sold", "vehicle": {"platform": "copart", "lot_number": "66412596"},
             "after": {"auction.last_sold_status": "Sold", "pricing.last_sold_price_usd": 875}}""");
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(sold), 150, false));
        when(client.searchVehiclesPage(firstPage())).thenReturn(vehicles(List.of(vehicle("1"), vehicle("2")), null, 2));

        SyncRun run = service.run("SCHEDULED");

        InOrder order = inOrder(client, lotService);
        order.verify(client).terminalChangesPage(100, 500);
        order.verify(lotService).applyOutcomes(argThat(o -> o.size() == 1 && o.get(0).lotNumber().equals("66412596")));
        order.verify(client).searchVehiclesPage(argThat(p -> p.get("type[]").equals(List.of("AUTOMOBILE", "SUV", "PICKUP"))
            && "0".equals(p.getFirst("auction_type")) && "only".equals(p.getFirst("upcoming"))
            && "2026-10-26".equals(p.getFirst("auction_date_from")) && "2026-10-26".equals(p.getFirst("auction_date_to"))
            && "RUNS AND DRIVES".equals(p.getFirst("run_cond")) && p.getFirst("year_from") == null));
        order.verify(lotService).saveOrUpdateLots(argThat(lots -> lots.size() == 2));
        assertThat(run.getStopReason()).isEqualTo("DONE");
        assertThat(stored.get(SyncStream.OUTCOMES).getSequence()).isEqualTo(150);
        assertThat(stored.get(TODAY).getLastFullAt()).isEqualTo(NOW);
        assertThat(stored.get(TODAY).getLastTotal()).isEqualTo(2);
        assertThat(stored.get(TODAY).getCursor()).isNull();
    }

    @Test
    void droppedLotsAreCountedPerRun() throws Exception {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(), 100, false));
        apibara.getFilters().setTitleExclude(List.of("DESTRUCTION"));
        JsonNode destroyed = vehicle("3");
        ((com.fasterxml.jackson.databind.node.ObjectNode) destroyed)
            .set("sale_document", objectMapper.createObjectNode().put("name", "CERTIFICATE OF DESTRUCTION (GA)"));
        JsonNode stationary = objectMapper.readTree(vehicle("4").toString().replace("RUNS AND DRIVES", "STATIONARY"));
        when(client.searchVehiclesPage(firstPage())).thenReturn(vehicles(List.of(vehicle("1"), destroyed, stationary), null, 3));

        SyncRun run = service.run("SCHEDULED");

        assertThat(run.getLotsFetched()).isEqualTo(3);
        assertThat(run.getSkippedCondition()).isEqualTo(1);
        assertThat(run.getSkippedTitle()).isEqualTo(1);
        assertThat(run.getSkippedType()).isZero();
        verify(lotService).saveOrUpdateLots(argThat(lots -> lots.size() == 1));
    }

    @Test
    void theFirstRunStartsOutcomesAtTheNewestEvent() {
        when(client.terminalChangesPage(0, 1)).thenReturn(new ApibaraChangesPage(List.of(), 11818394, true, 34414178L, null));
        when(client.searchVehiclesPage(any())).thenReturn(vehicles(List.of(), null, 0));

        service.run("SCHEDULED");

        assertThat(stored.get(SyncStream.OUTCOMES).getSequence()).isEqualTo(34414178);
        verify(lotService, never()).applyOutcomes(any());
    }

    @Test
    void theBudgetStopsAPassAndTheNextRunResumesIt() throws Exception {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(), 100, false));
        when(client.searchVehiclesPage(firstPage())).thenReturn(vehicles(List.of(vehicle("1")), "c1", 60));
        allowance(2);

        SyncRun first = service.run("SCHEDULED");

        assertThat(first.getStopReason()).isEqualTo("BUDGET");
        assertThat(stored.get(TODAY).getCursor()).isEqualTo("c1");
        assertThat(stored.get(TODAY).isBacklog()).isTrue();
        assertThat(stored.get(TODAY).getBacklogEstimate()).isEqualTo(40);
        assertThat(stored.get(TODAY).getLastFullAt()).isNull();

        when(client.searchVehiclesPage(withCursor("c1"))).thenReturn(vehicles(List.of(vehicle("2")), null, null));
        allowance(5);

        assertThat(service.run("MANUAL").getStopReason()).isEqualTo("DONE");
        verify(client).searchVehiclesPage(withCursor("c1"));
        assertThat(stored.get(TODAY).getLastFullAt()).isEqualTo(NOW);
        assertThat(stored.get(TODAY).getPagesDone()).isEqualTo(2);
    }

    @Test
    void aRejectedCursorStartsThePassAgain() {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(), 100, false));
        SyncStream today = new SyncStream(TODAY);
        today.setCursor("stale");
        stored.put(TODAY, today);
        when(client.searchVehiclesPage(withCursor("stale"))).thenThrow(new ApibaraRejectedException(422));
        when(client.searchVehiclesPage(firstPage())).thenReturn(vehicles(List.of(), null, 0));

        assertThat(service.run("SCHEDULED").getStopReason()).isEqualTo("DONE");
        assertThat(stored.get(TODAY).getLastFullAt()).isEqualTo(NOW);
    }

    @Test
    void theSequenceMovesOnlyAfterTheOutcomesCommitted() throws Exception {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(), 150, false));
        when(lotService.applyOutcomes(any())).thenThrow(new IllegalStateException("database down"));

        SyncRun run = service.run("SCHEDULED");

        assertThat(run.getStopReason()).isEqualTo("ERROR");
        assertThat(stored.get(SyncStream.OUTCOMES).getSequence()).isEqualTo(100);
        verify(client, never()).searchVehiclesPage(any());
    }

    @Test
    void anExpiredSequenceJumpsToTheNewest() {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenThrow(new ApibaraCursorExpiredException(34414178L));
        when(client.terminalChangesPage(34414178, 500)).thenReturn(changes(List.of(), 34414178, false));
        when(client.searchVehiclesPage(any())).thenReturn(vehicles(List.of(), null, 0));

        assertThat(service.run("SCHEDULED").getStopReason()).isEqualTo("DONE");
        assertThat(stored.get(SyncStream.OUTCOMES).getSequence()).isEqualTo(34414178);
    }

    @Test
    void aTooManyRequestsAnswerStopsTheRun() {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenThrow(new ApibaraQuotaExceededException(null, null));
        when(tracker.requestsSent()).thenReturn(4L, 5L);

        SyncRun run = service.run("SCHEDULED");

        assertThat(run.getStopReason()).isEqualTo("QUOTA_EXHAUSTED");
        assertThat(run.getApiRequests()).isEqualTo(1);
        verify(client, never()).searchVehiclesPage(any());
    }

    /** apibara.sync.enabled gates only the schedule: an admin's run works without it, within the budget */
    @Test
    void aManualRunWorksWhileTheScheduleIsDisabled() {
        props.setEnabled(false);
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(), 100, false));
        when(client.searchVehiclesPage(any())).thenReturn(vehicles(List.of(), null, 0));

        assertThat(service.run("MANUAL").getStopReason()).isEqualTo("DONE");
    }

    @Test
    void atKeepRemainingOrWithoutAKeyNothingIsSent() {
        outcomesAt(100);
        when(tracker.latest()).thenReturn(Optional.of(new ApibaraQuota(100, 90, 10, null, null, null, null, NOW)));
        assertThat(service.run("MANUAL", 5).getStopReason()).isEqualTo("QUOTA_EXHAUSTED");

        when(client.isConfigured()).thenReturn(false);
        assertThat(service.run("MANUAL").getStopReason()).isEqualTo("ERROR");

        verify(client, never()).terminalChangesPage(anyLong(), anyInt());
        verify(client, never()).searchVehiclesPage(any());
    }

    /** A manual limit may go beyond today's share, but never beyond what the period still allows */
    @Test
    void aManualLimitReplacesTodaysAllowanceUpToThePeriodCap() throws Exception {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(), 100, false));
        when(client.searchVehiclesPage(firstPage())).thenReturn(vehicles(List.of(vehicle("1")), "c1", 200));
        when(client.searchVehiclesPage(withCursor("c1"))).thenReturn(vehicles(List.of(vehicle("2")), "c1", null));
        allowance(2, 4); // today 2, the period 4

        assertThat(service.run("MANUAL", 10).getStopReason()).isEqualTo("BUDGET");

        // 1 outcomes request + 3 pages = the period's 4, not today's 2 and not the asked 10
        verify(client).searchVehiclesPage(firstPage());
        verify(client, org.mockito.Mockito.times(2)).searchVehiclesPage(withCursor("c1"));
    }

    @Test
    void aDayPassedRecentlyIsNotReadAgain() {
        outcomesAt(100);
        when(client.terminalChangesPage(100, 500)).thenReturn(changes(List.of(), 100, false));
        SyncStream today = new SyncStream(TODAY);
        today.setLastFullAt(NOW.minusSeconds(3600)); // refresh-hours.today = 4
        stored.put(TODAY, today);

        assertThat(service.run("MANUAL").getStopReason()).isEqualTo("DONE");
        verify(client, never()).searchVehiclesPage(any());
        verify(streams).deleteDaysBefore(eq("open:2026-10-25"));
    }
}
