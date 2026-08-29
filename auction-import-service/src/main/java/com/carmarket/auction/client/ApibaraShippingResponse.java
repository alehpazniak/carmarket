package com.carmarket.auction.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps the Apibara GET /vehicle-auction/vehicles/{slugVin}/shipping and
 * GET /vehicle-auction/shipping/auction-to-port responses — same shape either way.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApibaraShippingResponse(boolean ok, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
        Vehicle vehicle,
        @JsonProperty("auction_location") AuctionLocation auctionLocation,
        Shipping shipping
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(
        String platform,
        @JsonProperty("lot_number") String lotNumber,
        String vin,
        String title,
        String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuctionLocation(
        String display,
        @JsonProperty("facility_id") String facilityId,
        @JsonProperty("matched_location_id") String matchedLocationId,
        @JsonProperty("match_score") Integer matchScore
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shipping(
        @JsonProperty("recommended_port") String recommendedPort,
        @JsonProperty("recommended_price_usd") BigDecimal recommendedPriceUsd,
        @JsonProperty("has_shipping_price") Boolean hasShippingPrice,
        @JsonProperty("available_ports") List<Port> availablePorts
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Port(String port, BigDecimal price) {}
}
