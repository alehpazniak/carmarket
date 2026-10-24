package com.carmarket.auction.sync;

import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a GET /changes?terminal=1 event into a {@link LotOutcome}. As seen in Stage A (auction-feed-rollout.md,
 * Sizing): {@code after} is a flat map of dotted keys holding only what changed ("auction.last_sold_status",
 * "pricing.last_sold_price_usd", ...), values are strings or numbers, and local times carry no offset: they are
 * Apibara's server time, Kyiv. A lot.sold usually has no price (it only confirms a "Sold on Approval"), and a
 * lot.removed with last_sold_status Sold is a Buy Now sale.
 */
final class ChangeEventMapper {

    /** Apibara stores local times in Kyiv time (UTC+3 in summer, +2 in winter) */
    static final ZoneId APIBARA_ZONE = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ChangeEventMapper() {
    }

    static Optional<LotOutcome> toOutcome(JsonNode event) {
        JsonNode vehicle = event.path("vehicle");
        AuctionLot.AuctionSource source = switch (vehicle.path("platform").asText("").toLowerCase(Locale.ROOT)) {
            case "copart" -> AuctionLot.AuctionSource.COPART;
            case "iaai" -> AuctionLot.AuctionSource.IAAI;
            default -> null;
        };
        String lotNumber = vehicle.path("lot_number").asText(null);
        if (source == null || lotNumber == null || lotNumber.isBlank()) {
            return Optional.empty();
        }
        JsonNode after = event.path("after");
        AuctionLot.LotStatus status = switch (event.path("event").asText("")) {
            case "lot.sold" -> AuctionLot.LotStatus.SOLD;
            case "lot.not_sold" -> AuctionLot.LotStatus.UNSOLD;
            case "lot.removed" -> "sold".equalsIgnoreCase(after.path("auction.last_sold_status").asText(""))
                ? AuctionLot.LotStatus.SOLD : AuctionLot.LotStatus.REMOVED;
            default -> null;
        };
        if (status == null) {
            return Optional.empty();
        }
        Instant soldAt = instant(after.path("auction.last_sold_at"));
        Instant endedAt = Optional.ofNullable(instant(after.path("auction.auction_ended_at"))).orElse(soldAt);
        BigDecimal price = null;
        LocalDateTime saleDate = null;
        if (status == AuctionLot.LotStatus.SOLD) {
            price = Optional.ofNullable(decimal(after.path("pricing.sale_price_usd")))
                .orElse(decimal(after.path("pricing.last_sold_price_usd")));
            saleDate = soldAt != null ? LocalDateTime.ofInstant(soldAt, ZoneOffset.UTC) : day(after.path("auction.last_sold_day"));
        }
        return Optional.of(new LotOutcome(source, lotNumber.trim(), status, price, saleDate, endedAt));
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node.isNumber()) return node.decimalValue();
        if (node.isTextual() && !node.asText().isBlank()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** ISO with an offset, or "yyyy-MM-dd HH:mm:ss" in Apibara's Kyiv time */
    static Instant instant(JsonNode node) {
        if (!node.isTextual() || node.asText().isBlank()) return null;
        String text = node.asText().trim();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(text, LOCAL).atZone(APIBARA_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static LocalDateTime day(JsonNode node) {
        if (!node.isTextual()) return null;
        try {
            return LocalDate.parse(node.asText().trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
