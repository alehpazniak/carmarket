package com.carmarket.auction.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Maps the Apibara /vehicle-auction/vehicles response.
 * Every nested object and field is source-dependent → treat as nullable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApibaraResponse(List<Vehicle> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(
        String platform,
        @JsonProperty("lot_number") String lotNumber,
        String vin,
        String title,
        Integer year,
        String make,
        String model,
        Auction auction,
        Pricing pricing,
        Location location,
        Condition condition,
        Odometer odometer,
        Media media
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Auction(
        String state,
        @JsonProperty("auction_at") String auctionAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(
        @JsonProperty("current_bid_usd") java.math.BigDecimal currentBidUsd,
        @JsonProperty("buy_now_usd") java.math.BigDecimal buyNowUsd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(
        String display,
        @JsonProperty("send_from") String sendFrom
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(
        @JsonProperty("primary_damage") String primaryDamage,
        @JsonProperty("has_key") Boolean hasKey
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Odometer(Integer mi) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
        @JsonProperty("thumbs_count") Integer thumbsCount,
        @JsonProperty("has_video") Boolean hasVideo
    ) {}
}