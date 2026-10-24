package com.carmarket.auction.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Maps the Apibara /vehicle-auction/vehicles response.
 * Every nested object and field is source-dependent → treat as nullable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApibaraResponse(List<Vehicle> data, Meta meta) {

    /** Cursor pagination: next_cursor is opaque, passed back as-is; null on the last page. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("per_page") Integer perPage
    ) {}

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
        Media media,
        String type, // vehicle type, e.g. AUTOMOBILE, PICKUP; null on some records
        @JsonProperty("vehicle_specs") VehicleSpecs vehicleSpecs,
        @JsonProperty("sale_document") SaleDocument saleDocument
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VehicleSpecs(@JsonProperty("body_style") String bodyStyle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Auction(
        String state,
        @JsonProperty("auction_at") String auctionAt,
        // Outcome of an ended lot: Sold, Sold on Approval, Not Sold, Removed or null; older records only
        // carry last_sold_status
        @JsonProperty("outcome_status") String outcomeStatus,
        @JsonProperty("last_sold_status") String lastSoldStatus,
        @JsonProperty("last_sold_at") String lastSoldAt,
        @JsonProperty("last_sold_day") String lastSoldDay,
        @JsonProperty("auction_ended_at") String auctionEndedAt,
        @JsonProperty("lot_removed") Boolean lotRemoved
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(
        @JsonProperty("current_bid_usd") java.math.BigDecimal currentBidUsd,
        @JsonProperty("buy_now_usd") java.math.BigDecimal buyNowUsd,
        @JsonProperty("sale_price_usd") java.math.BigDecimal salePriceUsd,
        @JsonProperty("last_sold_price_usd") java.math.BigDecimal lastSoldPriceUsd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(
        String display,
        @JsonProperty("send_from") String sendFrom
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(
        @JsonProperty("primary_damage") String primaryDamage,
        @JsonProperty("has_key") Boolean hasKey,
        @JsonProperty("run_condition") RunCondition runCondition
    ) {}

    /** Apibara's normalized start/run state, e.g. value "RUNS AND DRIVES", "Run & Drive Verified", "ENGINE START PROGRAM" */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunCondition(String value, String label) {}

    /**
     * The title (Copart "Doc Type", IAAI "Title/Sale Doc"): name e.g. "SALVAGE CERTIFICATE", "CERTIFICATE OF
     * DESTRUCTION"; registration = Apibara's flag for US registrability (not a Polish rule).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaleDocument(String name, Boolean registration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Odometer(Integer mi) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
        @JsonProperty("thumbs_count") Integer thumbsCount,
        @JsonProperty("has_video") Boolean hasVideo,
        List<String> thumbs
    ) {}
}