package com.carmarket.auction.parser;

import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ApibaraAuctionParser.map: one /vehicles page → lots with their raw JSON. */
class ApibaraAuctionParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApibaraAuctionParser parser = new ApibaraAuctionParser(new ParserTargetsConfig(), objectMapper);

    private JsonNode vehicle(String lotNumber, String auctionAt) throws Exception {
        return objectMapper.readTree("""
            {"platform": "copart", "lot_number": "%s", "vin": "5UXTR9C54KLD98072", "year": 2019,
             "make": "BMW", "model": "X3", "type": "SUV", "seller": {"name": "Insurance Company"},
             "auction": {"state": "upcoming", "auction_at": "%s", "diff_minutes": 120},
             "pricing": {"current_bid_usd": 7200}, "location": {"display": "Tampa South (FL)"},
             "condition": {"primary_damage": "FRONT END"}, "media": {"thumbs": ["https://example.com/1.jpg"]}}
            """.formatted(lotNumber, auctionAt));
    }

    @Test
    void everyLotWithItsRawJsonAndTheAuctionInstant() throws Exception {
        JsonNode x3 = vehicle("58456596", "2026-08-07T16:00:00+02:00");

        ApibaraAuctionParser.MappedPage page = parser.map(List.of(x3, vehicle("2", "2026-08-08T14:00:00Z")));

        assertThat(page.lots()).extracting(AuctionLot::getLotNumber).containsExactly("58456596", "2");
        AuctionLot lot = page.lots().get(0);
        assertThat(lot.getAuctionDate()).isEqualTo(Instant.parse("2026-08-07T14:00:00Z")); // offset kept, as UTC
        assertThat(lot.getPrimaryDamage()).isEqualTo("FRONT END");
        assertThat(objectMapper.readTree(lot.getRawData())).isEqualTo(x3); // the whole lot, unmapped fields too
        assertThat(page.skippedType()).isZero();
    }

    @Test
    void incompleteOrUnmappableRecordsAreDropped() throws Exception {
        JsonNode noMake = objectMapper.readTree("{\"platform\": \"copart\", \"lot_number\": \"3\", \"year\": 2020}");
        JsonNode notAnObject = objectMapper.readTree("[1, 2]");

        assertThat(parser.map(List.of(noMake, notAnObject, vehicle("4", "2026-08-08T14:00:00Z"))).lots())
            .extracting(AuctionLot::getLotNumber).containsExactly("4");
    }

    @Test
    void unparseableDatesAreNull() {
        assertThat(ApibaraAuctionParser.parseDate("Aug 07, 2026 17:00")).isNull();
        assertThat(ApibaraAuctionParser.parseDate("2026-07-13T14:00:00+00:00")).isEqualTo(Instant.parse("2026-07-13T14:00:00Z"));
    }
}
