package com.carmarket.auction.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps the Apibara GET /vehicle-auction/vehicles/{slugVin} response.
 * Every nested object and field is source-dependent → treat as nullable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApibaraVehicleDetailResponse(boolean ok, Vehicle data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(
        @JsonProperty("slug_vin") String slugVin,
        String vin,
        String platform,
        @JsonProperty("lot_number") String lotNumber,
        String title,
        Integer year,
        String make,
        String model,
        String type,
        Auction auction,
        Pricing pricing,
        Location location,
        Seller seller,
        Condition condition,
        Odometer odometer,
        @JsonProperty("vehicle_specs") VehicleSpecs vehicleSpecs,
        @JsonProperty("sale_document") SaleDocument saleDocument,
        Media media
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Auction(
        String state,
        String formatted,
        @JsonProperty("auction_at") String auctionAt,
        @JsonProperty("is_timed") Boolean isTimed,
        @JsonProperty("is_buy_now") Boolean isBuyNow,
        @JsonProperty("timed_end_at") String timedEndAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(
        @JsonProperty("current_bid_usd") BigDecimal currentBidUsd,
        @JsonProperty("buy_now_usd") BigDecimal buyNowUsd,
        @JsonProperty("last_sold_price_usd") BigDecimal lastSoldPriceUsd,
        @JsonProperty("estimated_cost") EstimatedCost estimatedCost
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstimatedCost(BigDecimal from, BigDecimal to, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String display, @JsonProperty("send_from") String sendFrom, String state) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Seller(String name, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(
        @JsonProperty("run_condition") RunCondition runCondition,
        @JsonProperty("has_key") Boolean hasKey,
        String loss,
        @JsonProperty("primary_damage") String primaryDamage,
        @JsonProperty("secondary_damage") String secondaryDamage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunCondition(String value, String label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Odometer(Integer mi, Integer km) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VehicleSpecs(
        @JsonProperty("exterior_color") String exteriorColor,
        Engine engine,
        String transmission,
        @JsonProperty("fuel_type") String fuelType,
        @JsonProperty("drive_type") String driveType,
        @JsonProperty("body_style") String bodyStyle
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Engine(String raw, @JsonProperty("size_l") String sizeL, Integer hp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaleDocument(String name, String type, Boolean export, Boolean registration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
        @JsonProperty("thumbs_count") Integer thumbsCount,
        @JsonProperty("has_video") Boolean hasVideo,
        @JsonProperty("has_360") Boolean has360,
        List<String> thumbs,
        List<MediaItem> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaItem(String type, String thumb, String large, String url) {}
}
