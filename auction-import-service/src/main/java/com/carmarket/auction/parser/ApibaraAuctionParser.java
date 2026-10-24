package com.carmarket.auction.parser;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.client.ApibaraVehiclesPage;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
import java.util.function.Consumer;

/**
 * Pulls Copart lots from the Apibara API for each configured target
 * (see apibara.targets in application.yml) and maps them to AuctionLot entities.
 * Replaces the Playwright-based CopartParser — no browser, no scraping.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApibaraAuctionParser implements AuctionParser {

    private final ApibaraClient client;
    private final ParserTargetsConfig config;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(AuctionSource source) {
        return source == AuctionSource.COPART;
    }

    @Override
    public List<AuctionLot> parseLiveLots() {
        List<AuctionLot> lots = new ArrayList<>();
        forEachLiveLotPage(lots::addAll);
        return lots;
    }

    /**
     * One GET /vehicles per target. ApibaraQuotaExceededException (429) and ApibaraUnavailableException
     * end the run: the pages handed over before stay, and no further request spends quota.
     */
    @Override
    public void forEachLiveLotPage(Consumer<List<AuctionLot>> pageConsumer) {
        if (!client.isConfigured()) {
            log.warn("apibara.api-key is not set — nothing to fetch");
            return;
        }
        if (config.getTargets().isEmpty()) {
            log.warn("No apibara.targets configured — nothing to fetch");
            return;
        }

        for (ParserTargetsConfig.Target target : config.getTargets()) {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("platform", "copart");
            params.add("make", target.getMake());
            params.add("model", target.getModel());
            params.add("per_page", String.valueOf(client.effectivePerPage(config.getPerPage())));
            ApibaraVehiclesPage response = client.searchVehiclesPage(params);

            List<AuctionLot> page = new ArrayList<>();
            int skippedType = 0;
            for (JsonNode node : response.vehicles()) {
                ApibaraResponse.Vehicle v = toVehicle(node);
                // API filters make/model; we enforce the year range here
                if (v == null || !target.matchesYear(v.year())) continue;
                // Trucks, trailers, ATVs, ... even if the request's filter let them through
                if (!config.getVehicleTypes().includes(v.type())) {
                    skippedType++;
                    continue;
                }
                AuctionLot lot = toLot(v);
                if (lot != null) {
                    lot.setRawData(node.toString());
                    page.add(lot);
                }
            }
            log.info("Target {} {} [{}-{}]: {} fetched, {} kept after year filter, skipped_type={}",
                target.getMake(), target.getModel(),
                target.getYearFrom(), target.getYearTo(), response.vehicles().size(), page.size(), skippedType);
            if (!page.isEmpty()) {
                pageConsumer.accept(page);
            }
        }
    }

    private ApibaraResponse.Vehicle toVehicle(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, ApibaraResponse.Vehicle.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.debug("Skipping unmappable Apibara vehicle: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public AuctionLot parseLotDetail(String lotNumber) {
        // Optional: implement a by-lot lookup if you need single-lot refresh.
        // Apibara supports lookup by lot_number; add a client method when needed.
        log.debug("parseLotDetail not implemented for Apibara parser (lot {})", lotNumber);
        return null;
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
            .bodyStyle(v.vehicleSpecs() == null ? null : truncate(v.vehicleSpecs().bodyStyle()));
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
     *   <li>{@code lot_removed} → REMOVED;</li>
     *   <li>not ended → LIVE (an open lot's {@code last_sold_status} may belong to an earlier listing);</li>
     *   <li>ended: {@code outcome_status} (older records: {@code last_sold_status}) Sold → SOLD with
     *   {@code sale_price_usd}, Not Sold → UNSOLD, Removed → REMOVED; anything else, "Sold on Approval" included
     *   (the seller can still refuse), → EXPIRED, i.e. ended without a final result, until the result comes.</li>
     * </ul>
     */
    private void applyOutcome(AuctionLot.AuctionLotBuilder b, ApibaraResponse.Vehicle v) {
        ApibaraResponse.Auction auction = v.auction();
        if (auction == null) {
            b.status(AuctionLot.LotStatus.LIVE);
            return;
        }
        if (Boolean.TRUE.equals(auction.lotRemoved())) {
            b.status(AuctionLot.LotStatus.REMOVED);
            return;
        }
        if (!isEnded(auction)) {
            b.status(AuctionLot.LotStatus.LIVE);
            return;
        }
        b.endedAt(firstDate(auction.auctionEndedAt(), auction.lastSoldAt(), auction.auctionAt()));
        String outcome = auction.outcomeStatus() != null ? auction.outcomeStatus() : auction.lastSoldStatus();
        switch (outcome == null ? "" : outcome.trim().toLowerCase(Locale.ROOT)) {
            case "sold" -> {
                b.status(AuctionLot.LotStatus.SOLD);
                if (v.pricing() != null) {
                    b.soldPrice(v.pricing().salePriceUsd() != null ? v.pricing().salePriceUsd() : v.pricing().lastSoldPriceUsd());
                }
                b.saleDate(saleDate(auction));
            }
            case "not sold" -> b.status(AuctionLot.LotStatus.UNSOLD);
            case "removed" -> b.status(AuctionLot.LotStatus.REMOVED);
            default -> b.status(AuctionLot.LotStatus.EXPIRED);
        }
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

    /** vehicle_type / body_style are VARCHAR(50); body_style is free text */
    private static String truncate(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 50 ? trimmed : trimmed.substring(0, 50);
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