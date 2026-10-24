package com.carmarket.auction.sync;

import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Against the real GET /changes?terminal=1 answer recorded in Stage A (auction-feed-rollout.md, Sizing). */
class ChangeEventMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode recorded(int index) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/apibara/changes-terminal.json")) {
            return objectMapper.readTree(in).path("data").get(index);
        }
    }

    private JsonNode event(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    void aConfirmedSaleUsuallyHasNoPrice() throws Exception {
        LotOutcome outcome = ChangeEventMapper.toOutcome(recorded(0)).orElseThrow();

        assertThat(outcome.source()).isEqualTo(AuctionLot.AuctionSource.COPART);
        assertThat(outcome.lotNumber()).isEqualTo("66412596");
        assertThat(outcome.status()).isEqualTo(AuctionLot.LotStatus.SOLD);
        assertThat(outcome.price()).isNull(); // kept from the lot (its approval price)
        assertThat(outcome.saleDate()).isEqualTo(LocalDateTime.parse("2026-10-09T00:00:00"));
    }

    @Test
    void aChangedPriceComesWithTheSale() throws Exception {
        assertThat(ChangeEventMapper.toOutcome(recorded(1)).orElseThrow().price()).isEqualByComparingTo("875");
    }

    @Test
    void aRemovedLotThatSoldIsABuyNowSale() throws Exception {
        LotOutcome outcome = ChangeEventMapper.toOutcome(recorded(2)).orElseThrow();

        assertThat(outcome.lotNumber()).isEqualTo("70616046");
        assertThat(outcome.status()).isEqualTo(AuctionLot.LotStatus.SOLD);
        assertThat(outcome.price()).isEqualByComparingTo("50");
    }

    @Test
    void notSoldRemovedAndOtherEvents() throws Exception {
        assertThat(ChangeEventMapper.toOutcome(event("""
            {"event": "lot.not_sold", "vehicle": {"platform": "iaai", "lot_number": "45008746"}, "after": {}}"""))
            .orElseThrow()).extracting(LotOutcome::source, LotOutcome::status)
            .containsExactly(AuctionLot.AuctionSource.IAAI, AuctionLot.LotStatus.UNSOLD);
        assertThat(ChangeEventMapper.toOutcome(event("""
            {"event": "lot.removed", "vehicle": {"platform": "copart", "lot_number": "1"},
             "after": {"auction.lot_removed": 1}}""")).orElseThrow().status()).isEqualTo(AuctionLot.LotStatus.REMOVED);
        assertThat(ChangeEventMapper.toOutcome(event("""
            {"event": "bid.changed", "vehicle": {"platform": "copart", "lot_number": "1"}, "after": {}}"""))).isEmpty();
        assertThat(ChangeEventMapper.toOutcome(event("""
            {"event": "lot.sold", "vehicle": {"platform": "manheim", "lot_number": "1"}, "after": {}}"""))).isEmpty();
    }

    /** Local times without an offset are Apibara's Kyiv time (UTC+3 on 10 Oct) */
    @Test
    void anExactSaleTimeIsKyivTime() throws Exception {
        LotOutcome outcome = ChangeEventMapper.toOutcome(event("""
            {"event": "lot.sold", "vehicle": {"platform": "copart", "lot_number": "1"},
             "after": {"auction.last_sold_at": "2026-10-10 04:45:58", "pricing.sale_price_usd": "7900"}}""")).orElseThrow();

        assertThat(outcome.endedAt()).isEqualTo(Instant.parse("2026-10-10T01:45:58Z"));
        assertThat(outcome.saleDate()).isEqualTo(LocalDateTime.parse("2026-10-10T01:45:58"));
        assertThat(outcome.price()).isEqualByComparingTo("7900");
    }
}
