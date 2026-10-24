package com.carmarket.auction.parser;

import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps Apibara /vehicles records to AuctionLot entities for the feed sync (FeedSyncService), one response page at
 * a time. Drops what the sync doesn't keep, even if the request's filters let it through: vehicle types outside
 * apibara.vehicle-types.include, lots that aren't Run and Drive, and excluded titles (apibara.filters).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApibaraAuctionParser {

    private final ParserTargetsConfig config;
    private final ObjectMapper objectMapper;

    /** The lots of one page, each with its raw JSON, and how many records were dropped and why. */
    public record MappedPage(List<AuctionLot> lots, int skippedType, int skippedCondition, int skippedTitle) {
    }

    public MappedPage map(List<JsonNode> vehicles) {
        List<AuctionLot> lots = new ArrayList<>();
        int skippedType = 0;
        int skippedCondition = 0;
        int skippedTitle = 0;
        ParserTargetsConfig.Filters filters = config.getFilters();
        for (JsonNode node : vehicles) {
            ApibaraResponse.Vehicle v = toVehicle(node);
            if (v == null) continue;
            if (!config.getVehicleTypes().includes(v.type())) {
                skippedType++;
            } else if (!filters.acceptsRunCondition(runCondition(v))) {
                skippedCondition++;
            } else if (!filters.acceptsTitle(title(v))) {
                skippedTitle++;
            } else {
                AuctionLot lot = toLot(v);
                if (lot != null) {
                    lot.setRawData(node.toString());
                    lots.add(lot);
                }
            }
        }
        return new MappedPage(lots, skippedType, skippedCondition, skippedTitle);
    }

    private static String runCondition(ApibaraResponse.Vehicle v) {
        return v.condition() == null || v.condition().runCondition() == null ? null : v.condition().runCondition().value();
    }

    private static String title(ApibaraResponse.Vehicle v) {
        return v.saleDocument() == null ? null : v.saleDocument().name();
    }

    private ApibaraResponse.Vehicle toVehicle(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, ApibaraResponse.Vehicle.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.debug("Skipping unmappable Apibara vehicle: {}", e.getMessage());
            return null;
        }
    }

    /** Maps an Apibara vehicle to your AuctionLot entity, guarding every nullable field. */
    AuctionLot toLot(ApibaraResponse.Vehicle v) {
        AuctionLot.AuctionSource source = mapSource(v.platform());
        if (source == null || v.lotNumber() == null || v.make() == null || v.model() == null || v.year() == null) {
            return null; // required columns — skip incomplete records
        }

        AuctionLot.AuctionLotBuilder b = AuctionLot.builder()
            .vin(v.vin() != null ? v.vin() : "UNKNOWN")
            .make(v.make())
            .model(v.model())
            .year(v.year())
            .source(source)
            .lotNumber(v.lotNumber())
            .currency("USD")
            .vehicleType(truncate(v.type()))
            .bodyStyle(v.vehicleSpecs() == null ? null : truncate(v.vehicleSpecs().bodyStyle()))
            .runCondition(truncate(runCondition(v)))
            .titleType(truncate(title(v), 100));
        applyOutcome(b, v);

        if (v.pricing() != null) {
            b.auctionPrice(v.pricing().currentBidUsd());
            b.buyNowPrice(v.pricing().buyNowUsd());
        }
        if (v.condition() != null) {
            b.primaryDamage(v.condition().primaryDamage());
        }
        if (v.odometer() != null && v.odometer().mi() != null) {
            b.odometer(v.odometer().mi());
            b.odometerUnit("mi");
        }
        if (v.location() != null) {
            b.auctionLocation(v.location().display());
        }
        if (v.auction() != null && v.auction().auctionAt() != null) {
            b.auctionDate(parseDate(v.auction().auctionAt()));
        }
        if (v.media() != null && v.media().thumbs() != null) {
            b.imageUrls(v.media().thumbs());
        }

        return b.build();
    }

    private static AuctionLot.AuctionSource mapSource(String platform) {
        if (platform == null) return null;
        return switch (platform.toLowerCase(Locale.ROOT)) {
            case "copart" -> AuctionLot.AuctionSource.COPART;
            case "iaai" -> AuctionLot.AuctionSource.IAAI;
            default -> null;
        };
    }

    /**
     * Status, end time, sale date and final price from the auction's outcome, not from {@code state}: state is
     * "open" or "finished" whether the car sold or not, and "timed" is an auction mode, not a result.
     * <ul>
     *   <li>{@code lot_removed} → REMOVED, unless the outcome is Sold: a Buy Now sale also removes the lot;</li>
     *   <li>not ended → LIVE (an open lot's {@code last_sold_status} may belong to an earlier listing);</li>
     *   <li>ended: {@code outcome_status} (older records: {@code last_sold_status}) Sold → SOLD with
     *   {@code sale_price_usd}, Not Sold → UNSOLD, Removed → REMOVED; "Sold on Approval" (the seller can still
     *   refuse) → EXPIRED with the approval price in sold_price, because the confirming /changes event usually
     *   carries no price; anything else → EXPIRED, i.e. ended without a final result, until the result comes.</li>
     * </ul>
     */
    private void applyOutcome(AuctionLot.AuctionLotBuilder b, ApibaraResponse.Vehicle v) {
        ApibaraResponse.Auction auction = v.auction();
        if (auction == null) {
            b.status(AuctionLot.LotStatus.LIVE);
            return;
        }
        String outcome = auction.outcomeStatus() != null ? auction.outcomeStatus() : auction.lastSoldStatus();
        outcome = outcome == null ? "" : outcome.trim().toLowerCase(Locale.ROOT);
        boolean removed = Boolean.TRUE.equals(auction.lotRemoved());
        if (removed && !outcome.equals("sold")) {
            b.status(AuctionLot.LotStatus.REMOVED);
            return;
        }
        if (!removed && !isEnded(auction)) {
            b.status(AuctionLot.LotStatus.LIVE);
            return;
        }
        b.endedAt(firstDate(auction.auctionEndedAt(), auction.lastSoldAt(), auction.auctionAt()));
        switch (outcome) {
            case "sold" -> {
                b.status(AuctionLot.LotStatus.SOLD);
                b.soldPrice(salePrice(v.pricing()));
                b.saleDate(saleDate(auction));
            }
            case "not sold" -> b.status(AuctionLot.LotStatus.UNSOLD);
            case "removed" -> b.status(AuctionLot.LotStatus.REMOVED);
            case "sold on approval" -> {
                b.status(AuctionLot.LotStatus.EXPIRED);
                b.soldPrice(salePrice(v.pricing()));
            }
            default -> b.status(AuctionLot.LotStatus.EXPIRED);
        }
    }

    private static java.math.BigDecimal salePrice(ApibaraResponse.Pricing pricing) {
        if (pricing == null) return null;
        return pricing.salePriceUsd() != null ? pricing.salePriceUsd() : pricing.lastSoldPriceUsd();
    }

    /** auction_ended_at is set only for a past auction; older records only say "finished" */
    private static boolean isEnded(ApibaraResponse.Auction auction) {
        if (auction.auctionEndedAt() != null) return true;
        String state = auction.state() == null ? "" : auction.state().toLowerCase(Locale.ROOT);
        return ENDED_STATES.contains(state);
    }

    private static final Set<String> ENDED_STATES = Set.of("finished", "ended", "closed", "sold");

    /** The exact sale time when Apibara has it, else the end of the auction, else the sale day (UTC). */
    private static LocalDateTime saleDate(ApibaraResponse.Auction auction) {
        Instant exact = firstDate(auction.lastSoldAt(), auction.auctionEndedAt());
        if (exact != null) return LocalDateTime.ofInstant(exact, ZoneOffset.UTC);
        if (auction.lastSoldDay() == null) return null;
        try {
            return LocalDate.parse(auction.lastSoldDay()).atStartOfDay();
        } catch (DateTimeParseException e) {
            log.debug("Unparseable sale day: {}", auction.lastSoldDay());
            return null;
        }
    }

    private static Instant firstDate(String... isoDates) {
        for (String iso : isoDates) {
            Instant instant = iso == null ? null : parseDate(iso);
            if (instant != null) return instant;
        }
        return null;
    }

    /** vehicle_type / body_style / run_condition are VARCHAR(50); body_style is free text */
    private static String truncate(String value) {
        return truncate(value, 50);
    }

    /** title_type is VARCHAR(100) */
    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** Parses ISO-8601 with offset (e.g. "2026-07-13T14:00:00+00:00") to the instant, keeping the offset. */
    static Instant parseDate(String iso) {
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (DateTimeParseException e) {
            log.debug("Unparseable auction date: {}", iso);
            return null;
        }
    }
}