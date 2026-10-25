package com.carmarket.auction.service;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraVehicleDetailResponse;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.parser.ApibaraAuctionParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveVehicleLookupTest {

    private static final String COPART_URL =
        "https://www.copart.com/lot/61747696/salvage-2021-mazda-cx-5-grand-touring-il-chicago-south";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApibaraClient client = mock(ApibaraClient.class);
    private final AuctionLotService lotService = mock(AuctionLotService.class);
    private final ParserTargetsConfig config = new ParserTargetsConfig();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-25T10:00:00Z"));
    private LiveVehicleLookup lookup;

    /** A clock the test moves forward */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        config.getVehicleTypes().setInclude(List.of("SEDAN", "AUTOMOBILE", "SUV"));
        config.getFilters().setTitleExclude(List.of("DESTRUCTION", "JUNK"));
        lookup = new LiveVehicleLookup(client, new ApibaraVehicleResolver(client), new ApibaraAuctionParser(config, objectMapper),
            lotService, config, objectMapper, clock);
    }

    /** urltodetails-copart.json: 2021 Mazda CX-5, AUTOMOBILE, "Run & Drive Verified", SALVAGE CERTIFICATE */
    private JsonNode fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/apibara/urltodetails-copart.json")) {
            return objectMapper.readTree(in);
        }
    }

    @Test
    void aCarThatPassesTheSyncFiltersIsStoredAsALot() throws Exception {
        when(client.lookupVehicle("61747696")).thenReturn(Optional.of(fixture()));

        Optional<ApibaraVehicleDetailResponse> found = lookup.vehicle("61747696");

        assertThat(found).map(r -> r.data().vin()).contains("JM3KFBDM6M1463543");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuctionLot>> stored = ArgumentCaptor.forClass(List.class);
        verify(lotService).saveOrUpdateLots(stored.capture());
        assertThat(stored.getValue()).singleElement().satisfies(lot -> {
            assertThat(lot.getLotNumber()).isEqualTo("61747696");
            assertThat(lot.getSource()).isEqualTo(AuctionLot.AuctionSource.COPART);
            assertThat(lot.getRawData()).contains("\"lot_number\":\"61747696\"");
        });
    }

    @Test
    void aCarThatDoesntPassIsOnlyShown() throws Exception {
        JsonNode stationary = fixture();
        ((ObjectNode) stationary.path("data").path("condition").path("run_condition")).put("value", "STATIONARY");
        JsonNode junk = fixture();
        ((ObjectNode) junk.path("data").path("sale_document")).put("name", "JUNK (FL)");
        JsonNode trailer = fixture();
        ((ObjectNode) trailer.path("data")).put("type", "TRAILER");
        when(client.lookupVehicle("A0000001")).thenReturn(Optional.of(stationary));
        when(client.lookupVehicle("A0000002")).thenReturn(Optional.of(junk));
        when(client.lookupVehicle("A0000003")).thenReturn(Optional.of(trailer));

        assertThat(lookup.vehicle("A0000001")).isPresent();
        assertThat(lookup.vehicle("A0000002")).isPresent();
        assertThat(lookup.vehicle("A0000003")).isPresent();
        verify(lotService, never()).saveOrUpdateLots(any());
    }

    @Test
    void aFailedStoreDoesntFailTheLookup() throws Exception {
        when(client.lookupVehicle("61747696")).thenReturn(Optional.of(fixture()));
        when(lotService.saveOrUpdateLots(any())).thenThrow(new IllegalStateException("database down"));

        assertThat(lookup.vehicle("61747696")).isPresent();
    }

    @Test
    void aRepeatedLookupIsServedFromTheCacheUntilItExpires() throws Exception {
        when(client.lookupVehicle(anyString())).thenReturn(Optional.of(fixture()));

        lookup.vehicle("jm3kfbdm6m1463543");
        lookup.vehicle(" JM3KFBDM6M1463543 ");
        lookup.vehicle("61747696"); // the same car by its lot number
        verify(client, times(1)).lookupVehicle(anyString());
        verify(lotService, times(1)).saveOrUpdateLots(any()); // stored once, not on every view

        clock.advance(Duration.ofMinutes(10)); // apibara.live-lookup.cache-minutes
        lookup.vehicle("JM3KFBDM6M1463543");
        verify(client, times(2)).lookupVehicle(anyString());
    }

    /** /aukcje resolves a link, then opens /aukcje/pojazd/{vin}: that page's lookup costs nothing */
    @Test
    void aResolvedLinkIsCachedUnderItsVinAndLotNumber() throws Exception {
        when(client.lookupUrl(COPART_URL)).thenReturn(Optional.of(fixture()));

        assertThat(lookup.resolve(COPART_URL)).isPresent();
        assertThat(lookup.vehicle("JM3KFBDM6M1463543")).isPresent();
        assertThat(lookup.vehicle("61747696")).isPresent();
        assertThat(lookup.resolve(COPART_URL)).isPresent();

        verify(client, times(1)).lookupUrl(COPART_URL);
        verify(client, never()).lookupVehicle(anyString());
    }

    @Test
    void notFoundIsNotCached() {
        when(client.lookupVehicle("99999999")).thenReturn(Optional.empty());

        assertThat(lookup.vehicle("99999999")).isEmpty();
        assertThat(lookup.vehicle("99999999")).isEmpty();
        verify(client, times(2)).lookupVehicle("99999999");
    }

    @Test
    void cacheMinutesZeroTurnsTheCacheOff() throws Exception {
        config.getLiveLookup().setCacheMinutes(0);
        when(client.lookupVehicle("61747696")).thenReturn(Optional.of(fixture()));

        lookup.vehicle("61747696");
        lookup.vehicle("61747696");
        verify(client, times(2)).lookupVehicle("61747696");
    }
}
