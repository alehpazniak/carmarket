package com.carmarket.auction.parser;

import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ApibaraAuctionParser.toLot against the recorded Apibara responses, and the vehicle-type filter. */
class ApibaraLotMappingTest {

    private static final Path FIXTURES = Path.of("src/test/java/com/carmarket/auction");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ParserTargetsConfig config = new ParserTargetsConfig();
    private final ApibaraAuctionParser parser = new ApibaraAuctionParser(config, objectMapper);

    private JsonNode fixture(Path file) throws Exception {
        return objectMapper.readTree(Files.readString(file)).path("data");
    }

    private AuctionLot map(JsonNode vehicle) throws Exception {
        return parser.toLot(objectMapper.treeToValue(vehicle, ApibaraResponse.Vehicle.class));
    }

    /** The open Mazda fixture with its auction block replaced. */
    private AuctionLot withAuction(String auctionJson) throws Exception {
        ObjectNode vehicle = (ObjectNode) fixture(FIXTURES.resolve("apibara.tech_api_v1_vehicle-auction_vehicles_JM3KFBDM7K1621899.json"));
        vehicle.set("auction", objectMapper.readTree(auctionJson));
        vehicle.set("pricing", objectMapper.readTree("{\"current_bid_usd\": 3550, \"sale_price_usd\": 4100}"));
        return map(vehicle);
    }

    @Test
    void openLotIsLiveWithTypeAndBodyStyle() throws Exception {
        AuctionLot lot = map(fixture(FIXTURES.resolve("apibara.tech_api_v1_vehicle-auction_vehicles_JM3KFBDM7K1621899.json")));

        assertThat(lot.getStatus()).isEqualTo(AuctionLot.LotStatus.LIVE);
        assertThat(lot.getSource()).isEqualTo(AuctionLot.AuctionSource.IAAI); // this record is IAAI's
        assertThat(lot.getVehicleType()).isEqualTo("AUTOMOBILE");
        assertThat(lot.getBodyStyle()).isEqualTo("SUV/Crossover");
        assertThat(lot.getSoldPrice()).isNull();
        assertThat(lot.getEndedAt()).isNull();
    }

    /** Current records: outcome_status, sale_price_usd and the exact last_sold_at. */
    @Test
    void soldLotHasItsFinalPriceSaleDateAndEnd() throws Exception {
        AuctionLot lot = map(objectMapper.readTree(Files.readString(Path.of("src/test/resources/apibara/urltodetails-copart.json"))).path("data"));

        assertThat(lot.getStatus()).isEqualTo(AuctionLot.LotStatus.SOLD);
        assertThat(lot.getSoldPrice()).isEqualByComparingTo("7900");
        assertThat(lot.getSaleDate()).isEqualTo(LocalDateTime.parse("2026-09-09T18:30:05"));
        assertThat(lot.getEndedAt()).isEqualTo(Instant.parse("2026-09-09T18:30:05Z"));
        assertThat(lot.getVehicleType()).isEqualTo("AUTOMOBILE");
        assertThat(lot.getBodyStyle()).isEqualTo("Sport utility vehicle");
    }

    /** Older records: no outcome_status, only last_sold_status, last_sold_price_usd and the sale day; no type. */
    @Test
    void olderSoldRecordFallsBackToLastSoldFields() throws Exception {
        AuctionLot lot = map(fixture(FIXTURES.resolve("apibara.tech_api_v1_vehicle-auction_vehicles_5UXTR9C54KLD98072.json")));

        assertThat(lot.getStatus()).isEqualTo(AuctionLot.LotStatus.SOLD); // state "finished" used to give UNSOLD
        assertThat(lot.getSoldPrice()).isEqualByComparingTo("10400");
        assertThat(lot.getSaleDate()).isEqualTo(LocalDateTime.parse("2026-08-07T00:00:00"));
        assertThat(lot.getEndedAt()).isEqualTo(Instant.parse("2026-08-07T14:00:00Z"));
        assertThat(lot.getVehicleType()).isNull();
    }

    @Test
    void everyOutcome() throws Exception {
        String ended = "\"state\": \"finished\", \"auction_at\": \"2026-10-01T14:00:00+00:00\", \"auction_ended_at\": \"2026-10-01T15:10:00+00:00\"";

        AuctionLot notSold = withAuction("{" + ended + ", \"outcome_status\": \"Not Sold\"}");
        assertThat(notSold.getStatus()).isEqualTo(AuctionLot.LotStatus.UNSOLD);
        assertThat(notSold.getSoldPrice()).isNull();
        assertThat(notSold.getEndedAt()).isEqualTo(Instant.parse("2026-10-01T15:10:00Z"));

        assertThat(withAuction("{" + ended + ", \"outcome_status\": \"Sold\"}").getSoldPrice()).isEqualByComparingTo("4100");
        assertThat(withAuction("{" + ended + ", \"outcome_status\": \"Removed\"}").getStatus()).isEqualTo(AuctionLot.LotStatus.REMOVED);
        assertThat(withAuction("{\"state\": \"open\", \"lot_removed\": true}").getStatus()).isEqualTo(AuctionLot.LotStatus.REMOVED);

        // Sold on Approval isn't final, and an ended lot without an outcome has no result yet
        AuctionLot onApproval = withAuction("{" + ended + ", \"outcome_status\": \"Sold on Approval\"}");
        assertThat(onApproval.getStatus()).isEqualTo(AuctionLot.LotStatus.EXPIRED);
        AuctionLot noResult = withAuction("{" + ended + "}");
        assertThat(noResult.getStatus()).isEqualTo(AuctionLot.LotStatus.EXPIRED);
        assertThat(noResult.getSoldPrice()).isNull();

        // "timed" is an auction mode; an open lot's last_sold_status belongs to an earlier listing
        assertThat(withAuction("{\"state\": \"timed\", \"auction_at\": \"2026-11-01T14:00:00+00:00\"}").getStatus())
            .isEqualTo(AuctionLot.LotStatus.LIVE);
        assertThat(withAuction("{\"state\": \"open\", \"last_sold_status\": \"Not Sold\", \"last_sold_day\": \"2026-09-01\"}").getStatus())
            .isEqualTo(AuctionLot.LotStatus.LIVE);
    }

    @Test
    void sourceComesFromThePlatform() throws Exception {
        ObjectNode vehicle = (ObjectNode) fixture(FIXTURES.resolve("apibara.tech_api_v1_vehicle-auction_vehicles_JM3KFBDM7K1621899.json"));
        vehicle.put("platform", "copart");
        assertThat(map(vehicle).getSource()).isEqualTo(AuctionLot.AuctionSource.COPART);

        vehicle.put("platform", "manheim");
        assertThat(map(vehicle)).isNull();
    }

    @Test
    void excludedTypesAreSkipped() throws Exception {
        config.getVehicleTypes().setInclude(List.of("AUTOMOBILE", "SUV", "PICKUP"));

        List<JsonNode> vehicles = new ArrayList<>();
        for (String type : new String[]{"PICKUP", "TRUCK", "ATV", "pickup", null}) {
            ObjectNode vehicle = (ObjectNode) fixture(FIXTURES.resolve("apibara.tech_api_v1_vehicle-auction_vehicles_JM3KFBDM7K1621899.json"));
            vehicle.put("lot_number", "lot-" + type);
            vehicle.put("type", type);
            vehicles.add(vehicle);
        }

        ApibaraAuctionParser.MappedPage page = parser.map(vehicles);

        // TRUCK and ATV are dropped; a record without a type is kept
        assertThat(page.lots()).extracting(AuctionLot::getLotNumber).containsExactly("lot-PICKUP", "lot-pickup", "lot-null");
        assertThat(page.skippedType()).isEqualTo(2);
    }

    /** A Buy Now sale also removes the lot from inventory; the sale wins (seen in /changes, Sizing). */
    @Test
    void aBuyNowSaleIsSoldNotRemoved() throws Exception {
        AuctionLot lot = withAuction("{\"state\": \"open\", \"lot_removed\": true, \"last_sold_status\": \"Sold\", "
            + "\"last_sold_day\": \"2026-10-10\", \"auction_at\": \"2026-10-10T01:45:58+00:00\"}");

        assertThat(lot.getStatus()).isEqualTo(AuctionLot.LotStatus.SOLD);
        assertThat(lot.getSoldPrice()).isEqualByComparingTo("4100");
    }

    /** /changes confirms it later, usually without a price, so the approval price is kept meanwhile. */
    @Test
    void soldOnApprovalKeepsItsProvisionalPrice() throws Exception {
        AuctionLot lot = withAuction("{\"state\": \"finished\", \"auction_ended_at\": \"2026-10-01T15:10:00+00:00\", "
            + "\"outcome_status\": \"Sold on Approval\"}");

        assertThat(lot.getStatus()).isEqualTo(AuctionLot.LotStatus.EXPIRED);
        assertThat(lot.getSoldPrice()).isEqualByComparingTo("4100");
    }
}
