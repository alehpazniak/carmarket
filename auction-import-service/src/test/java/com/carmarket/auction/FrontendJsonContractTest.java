package com.carmarket.auction;

import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.dto.CalculationRequest;
import com.carmarket.auction.dto.ImportCalculationResponse;
import com.carmarket.auction.dto.LotFacetsResponse;
import com.carmarket.auction.dto.MaxBidRequest;
import com.carmarket.auction.dto.MaxBidResponse;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.service.shipping.ContainerShare;
import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.OceanFreightSource;
import com.carmarket.auction.service.shipping.PolandZone;
import com.carmarket.auction.service.shipping.W8MatchType;
import com.carmarket.auction.service.shipping.W8PortOption;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;
import java.time.Instant;
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
            .auctionDate(Instant.parse("2026-10-01T14:00:00Z"))
            .rawData("{\"lot_number\":\"45008746\"}").contentHash("abc")
            .vehicleType("AUTOMOBILE").runCondition("RUNS AND DRIVES").titleType("SALVAGE CERTIFICATE (FL)")
            .soldPrice(new BigDecimal("7900")).lastSeenAt(Instant.parse("2026-10-01T15:00:00Z"))
            .build();

        JsonNode json = toJson(lot);

        assertThat(json.path("lotNumber").asText()).isEqualTo("45008746");
        assertThat(json.path("auctionLocation").asText()).isEqualTo("CA");
        assertThat(json.path("saleDate").asText()).startsWith("2026-10-01T12:00");
        assertThat(json.has("lot_number")).isFalse();
        assertThat(json.path("vehicleType").asText()).isEqualTo("AUTOMOBILE");
        assertThat(json.path("runCondition").asText()).isEqualTo("RUNS AND DRIVES");
        assertThat(json.path("titleType").asText()).isEqualTo("SALVAGE CERTIFICATE (FL)");
        assertThat(json.path("soldPrice").decimalValue()).isEqualByComparingTo("7900");
        // ISO in UTC; the frontend converts to Europe/Warsaw
        assertThat(json.path("auctionDate").asText()).isEqualTo("2026-10-01T14:00:00Z");
        assertThat(json.has("rawData")).isFalse();
        assertThat(json.has("contentHash")).isFalse();
        assertThat(json.has("lastSeenAt")).isFalse();
    }

    @Test
    void lotFacetsAreCamelCase() throws Exception {
        JsonNode make = toJson(LotFacetsResponse.from(List.of(
            new LotFacetsResponse.Row("TOYOTA", "RAV4", 7L), new LotFacetsResponse.Row("TOYOTA", "CAMRY", 5L))))
            .path("makes").get(0);

        assertThat(make.path("make").asText()).isEqualTo("TOYOTA");
        assertThat(make.path("count").asLong()).isEqualTo(12);
        assertThat(make.path("models").get(0).path("model").asText()).isEqualTo("CAMRY");
        assertThat(make.path("models").get(0).path("count").asLong()).isEqualTo(5);
        assertThat(make.path("models").get(1).path("model").asText()).isEqualTo("RAV4");
    }

    @Test
    void apibaraVehicleIsSnakeCase() throws Exception {
        ApibaraResponse.Vehicle vehicle = new ApibaraResponse.Vehicle(
            "copart", "45008746", "JTNABAAE4PA006723", "2023 TOYOTA GR COROLLA", 2023, "TOYOTA", "COROLLA",
            new ApibaraResponse.Auction("upcoming", "2026-10-01T12:00:00Z", null, null, null, null, null, null),
            new ApibaraResponse.Pricing(new BigDecimal("1500"), null, null, null),
            new ApibaraResponse.Location("Sacramento (CA)", "CA"),
            new ApibaraResponse.Condition("FRONT END", true, new ApibaraResponse.RunCondition("RUNS AND DRIVES", "Runs and drives")),
            new ApibaraResponse.Odometer(12000),
            new ApibaraResponse.Media(12, false, List.of("https://example.com/1.jpg")),
            "AUTOMOBILE", new ApibaraResponse.VehicleSpecs("Sedan"),
            new ApibaraResponse.SaleDocument("SALVAGE CERTIFICATE", true));

        JsonNode json = toJson(vehicle);
        assertThat(json.path("vehicle_specs").path("body_style").asText()).isEqualTo("Sedan");
        assertThat(json.path("condition").path("run_condition").path("value").asText()).isEqualTo("RUNS AND DRIVES");
        assertThat(json.path("sale_document").path("name").asText()).isEqualTo("SALVAGE CERTIFICATE");

        assertThat(json.path("lot_number").asText()).isEqualTo("45008746");
        assertThat(json.path("pricing").path("current_bid_usd").decimalValue()).isEqualByComparingTo("1500");
        assertThat(json.path("media").path("thumbs_count").asInt()).isEqualTo(12);
        assertThat(json.has("lotNumber")).isFalse();
    }

    @Test
    void apibaraSearchMetaIsSnakeCase() throws Exception {
        JsonNode meta = toJson(new ApibaraResponse(List.of(), new ApibaraResponse.Meta("eyJhIjoxfQ", 20))).path("meta");

        assertThat(meta.path("next_cursor").asText()).isEqualTo("eyJhIjoxfQ");
        assertThat(meta.path("per_page").asInt()).isEqualTo(20);
        assertThat(objectMapper.readValue("{\"data\": [], \"meta\": {\"next_cursor\": \"c\", \"per_page\": 40, \"has_more\": true}}",
            ApibaraResponse.class).meta().nextCursor()).isEqualTo("c");
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

    @Test
    void w8QuoteIsCamelCaseOnBothCalculators() throws Exception {
        W8PortOption option = new W8PortOption(W8Port.NEW_YORK, "New York (Linden, NJ)", new BigDecimal("175"),
            new BigDecimal("525"), OceanFreightSource.W8_PRICE_LIST, 25, 30, new BigDecimal("700"));
        W8ShippingQuote quote = new W8ShippingQuote("W8-2025-26", W8MatchType.EXACT, "SOMERVILLE - NJ", W8Auction.COPART,
            ContainerShare.QUARTER, List.of(option), option, new BigDecimal("430"), new BigDecimal("100"),
            new BigDecimal("50"), PolandZone.A, new BigDecimal("2300"), new BigDecimal("75"), new BigDecimal("125"),
            new BigDecimal("700"), new BigDecimal("4794.00"), new BigDecimal("200"), new BigDecimal("4.00"),
            new BigDecimal("4.30"));

        JsonNode maxBid = toJson(MaxBidResponse.builder().w8(quote).build()).path("w8");
        assertThat(maxBid.path("matchType").asText()).isEqualTo("EXACT");
        assertThat(maxBid.path("chosen").path("portLabel").asText()).isEqualTo("New York (Linden, NJ)");
        assertThat(maxBid.path("chosen").path("oceanFreightSource").asText()).isEqualTo("W8_PRICE_LIST");
        assertThat(maxBid.path("options").get(0).path("toEuBorderUsd").decimalValue()).isEqualByComparingTo("700");
        assertThat(maxBid.path("afterBorderPln").decimalValue()).isEqualByComparingTo("4794.00");
        assertThat(maxBid.path("polandZone").asText()).isEqualTo("A");
        assertThat(maxBid.has("match_type")).isFalse();

        assertThat(toJson(ImportCalculationResponse.builder().w8(quote).build()).path("w8").path("containerShare").asText())
            .isEqualTo("QUARTER");
        assertThat(toJson(MaxBidResponse.builder().build()).path("w8").isNull()).isTrue();
    }

    @Test
    void maxBidRequestReadsTheW8Fields() throws Exception {
        MaxBidRequest request = objectMapper.readValue("""
            {"budgetPln": 100000, "suv": true, "auction": "copart", "auctionLocation": "Somerville (NJ)",
             "polandZone": "B", "lengthClass": "OVER_5_3M", "vin": "5UXCR6C5XL9B12345"}""", MaxBidRequest.class);

        assertThat(request.getAuction()).isEqualTo("copart");
        assertThat(request.getAuctionLocation()).isEqualTo("Somerville (NJ)");
        assertThat(request.getPolandZone()).isEqualTo(PolandZone.B);
        assertThat(request.getLengthClass()).isEqualTo(LengthClass.OVER_5_3M);

        CalculationRequest calc = objectMapper.readValue("""
            {"targetSalePricePln": 90000, "suv": false, "polandZone": "C", "lengthClass": "OVER_5M"}""", CalculationRequest.class);
        assertThat(calc.getPolandZone()).isEqualTo(PolandZone.C);
        assertThat(calc.getLengthClass()).isEqualTo(LengthClass.OVER_5M);
    }

    private JsonNode toJson(Object value) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(value));
    }
}
