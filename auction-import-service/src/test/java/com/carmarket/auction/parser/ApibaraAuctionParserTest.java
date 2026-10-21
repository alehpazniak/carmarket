package com.carmarket.auction.parser;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraQuotaExceededException;
import com.carmarket.auction.client.ApibaraVehiclesPage;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApibaraAuctionParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApibaraClient client = mock(ApibaraClient.class);
    private final ParserTargetsConfig config = new ParserTargetsConfig();
    private final ApibaraAuctionParser parser = new ApibaraAuctionParser(client, config, objectMapper);

    @BeforeEach
    void configured() {
        when(client.isConfigured()).thenReturn(true);
        when(client.effectivePerPage(anyInt())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static boolean forModel(MultiValueMap<String, String> params, String model) {
        return params != null && model.equals(params.getFirst("model"));
    }

    private void respond(String model, JsonNode... vehicles) {
        when(client.searchVehiclesPage(argThat(p -> forModel(p, model))))
            .thenReturn(new ApibaraVehiclesPage(List.of(vehicles), null, null));
    }

    private List<List<AuctionLot>> pages() {
        List<List<AuctionLot>> pages = new ArrayList<>();
        parser.forEachLiveLotPage(pages::add);
        return pages;
    }

    private static ParserTargetsConfig.Target target(String make, String model) {
        ParserTargetsConfig.Target target = new ParserTargetsConfig.Target();
        target.setMake(make);
        target.setModel(model);
        target.setYearFrom(2018);
        target.setYearTo(2022);
        return target;
    }

    private JsonNode vehicle(String lotNumber, int year, String auctionAt) throws Exception {
        return objectMapper.readTree("""
            {"platform": "copart", "lot_number": "%s", "vin": "5UXTR9C54KLD98072", "year": %d,
             "make": "BMW", "model": "X3", "seller": {"name": "Insurance Company"},
             "auction": {"state": "upcoming", "auction_at": "%s", "diff_minutes": 120},
             "pricing": {"current_bid_usd": 7200}, "location": {"display": "Tampa South (FL)"},
             "condition": {"primary_damage": "FRONT END"}, "media": {"thumbs": ["https://example.com/1.jpg"]}}
            """.formatted(lotNumber, year, auctionAt));
    }

    @Test
    void onePagePerTargetWithTheLotsRawJsonAndTheAuctionInstant() throws Exception {
        config.setPerPage(40);
        config.setTargets(List.of(target("BMW", "X3"), target("Toyota", "RAV4")));
        JsonNode x3 = vehicle("58456596", 2019, "2026-08-07T16:00:00+02:00");
        respond("X3", x3, vehicle("1", 2010, "2026-08-07T14:00:00Z"));
        respond("RAV4", vehicle("2", 2020, "2026-08-08T14:00:00Z"));

        List<List<AuctionLot>> pages = pages();

        assertThat(pages).hasSize(2);
        AuctionLot lot = pages.get(0).get(0);
        assertThat(pages.get(0)).hasSize(1); // the 2010 car is outside the year range
        assertThat(lot.getLotNumber()).isEqualTo("58456596");
        assertThat(lot.getAuctionDate()).isEqualTo(Instant.parse("2026-08-07T14:00:00Z")); // offset kept, as UTC
        assertThat(lot.getPrimaryDamage()).isEqualTo("FRONT END");
        assertThat(objectMapper.readTree(lot.getRawData())).isEqualTo(x3); // the whole lot, unmapped fields too
        assertThat(pages.get(1)).extracting(AuctionLot::getLotNumber).containsExactly("2");
        assertThat(parser.parseLiveLots()).hasSize(2);
        verify(client, org.mockito.Mockito.atLeastOnce()).searchVehiclesPage(argThat(p -> p != null
            && "copart".equals(p.getFirst("platform")) && "40".equals(p.getFirst("per_page"))));
    }

    @Test
    void aQuotaErrorStopsTheRunAfterTheEarlierPagesWereHandedOver() throws Exception {
        config.setTargets(List.of(target("BMW", "X3"), target("Toyota", "RAV4"), target("Audi", "A4")));
        respond("X3", vehicle("1", 2019, "2026-08-07T14:00:00Z"));
        when(client.searchVehiclesPage(argThat(p -> forModel(p, "RAV4"))))
            .thenThrow(new ApibaraQuotaExceededException(null, null));
        List<List<AuctionLot>> handedOver = new ArrayList<>();

        assertThatThrownBy(() -> parser.forEachLiveLotPage(handedOver::add))
            .isInstanceOf(ApibaraQuotaExceededException.class);

        assertThat(handedOver).hasSize(1);
        verify(client, never()).searchVehiclesPage(argThat(p -> forModel(p, "A4")));
    }

    @Test
    void withoutAKeyNoRequest() {
        when(client.isConfigured()).thenReturn(false);
        config.setTargets(List.of(target("BMW", "X3")));

        assertThat(pages()).isEmpty();
        verify(client, never()).searchVehiclesPage(any());
    }

    @Test
    void unparseableDatesAreNull() {
        assertThat(ApibaraAuctionParser.parseDate("Aug 07, 2026 17:00")).isNull();
        assertThat(ApibaraAuctionParser.parseDate("2026-07-13T14:00:00+00:00")).isEqualTo(Instant.parse("2026-07-13T14:00:00Z"));
    }
}
