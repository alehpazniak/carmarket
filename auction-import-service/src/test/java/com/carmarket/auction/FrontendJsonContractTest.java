package com.carmarket.auction;

import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.dto.ImportCalculationResponse;
import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSON keys avtovo-frontend/src/types/auctions.ts reads. The backend mixes camelCase (entities, Lombok
 * DTOs) and snake_case (@JsonProperty on the Apibara records), so renaming a field on either side must break
 * this test rather than blank out the UI. Uses the application's auto-configured ObjectMapper.
 */
@JsonTest
class FrontendJsonContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void auctionLotIsCamelCase() throws Exception {
        AuctionLot lot = AuctionLot.builder()
            .vin("JTNABAAE4PA006723").make("TOYOTA").model("COROLLA").year(2023)
            .source(AuctionLot.AuctionSource.IAAI).lotNumber("45008746")
            .auctionLocation("CA").saleDate(LocalDateTime.of(2026, 10, 1, 12, 0))
            .build();

        JsonNode json = toJson(lot);

        assertThat(json.path("lotNumber").asText()).isEqualTo("45008746");
        assertThat(json.path("auctionLocation").asText()).isEqualTo("CA");
        assertThat(json.path("saleDate").asText()).startsWith("2026-10-01T12:00");
        assertThat(json.has("lot_number")).isFalse();
    }

    @Test
    void apibaraVehicleIsSnakeCase() throws Exception {
        ApibaraResponse.Vehicle vehicle = new ApibaraResponse.Vehicle(
            "copart", "45008746", "JTNABAAE4PA006723", "2023 TOYOTA GR COROLLA", 2023, "TOYOTA", "COROLLA",
            new ApibaraResponse.Auction("upcoming", "2026-10-01T12:00:00Z"),
            new ApibaraResponse.Pricing(new BigDecimal("1500"), null),
            new ApibaraResponse.Location("Sacramento (CA)", "CA"),
            new ApibaraResponse.Condition("FRONT END", true),
            new ApibaraResponse.Odometer(12000),
            new ApibaraResponse.Media(12, false, List.of("https://example.com/1.jpg")));

        JsonNode json = toJson(vehicle);

        assertThat(json.path("lot_number").asText()).isEqualTo("45008746");
        assertThat(json.path("pricing").path("current_bid_usd").decimalValue()).isEqualByComparingTo("1500");
        assertThat(json.path("media").path("thumbs_count").asInt()).isEqualTo(12);
        assertThat(json.has("lotNumber")).isFalse();
    }

    @Test
    void apibaraShippingIsSnakeCase() throws Exception {
        ApibaraShippingResponse shipping = new ApibaraShippingResponse(true, new ApibaraShippingResponse.Data(
            new ApibaraShippingResponse.Vehicle("iaai", "45008746", "JTNABAAE4PA006723", "2023 TOYOTA", "AUTOMOBILE"),
            new ApibaraShippingResponse.AuctionLocation("Sacramento (CA)", null, "Sacramento (CA)", 100),
            new ApibaraShippingResponse.Shipping("LA", new BigDecimal("400"), true,
                List.of(new ApibaraShippingResponse.Port("LA", new BigDecimal("400"))))));

        JsonNode data = toJson(shipping).path("data");

        assertThat(data.path("auction_location").path("display").asText()).isEqualTo("Sacramento (CA)");
        assertThat(data.path("vehicle").path("lot_number").asText()).isEqualTo("45008746");
        assertThat(data.path("shipping").path("recommended_price_usd").decimalValue()).isEqualByComparingTo("400");
        assertThat(data.has("auctionLocation")).isFalse();
    }

    @Test
    void importCalculationIsCamelCase() throws Exception {
        ImportCalculationResponse calculation = ImportCalculationResponse.builder()
            .usDelivery(new BigDecimal("350"))
            .build();

        JsonNode json = toJson(calculation);

        assertThat(json.path("usDelivery").decimalValue()).isEqualByComparingTo("350");
        assertThat(json.has("us_delivery")).isFalse();
    }

    private JsonNode toJson(Object value) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(value));
    }
}
