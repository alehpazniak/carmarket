package com.carmarket.auction.parser;

import com.carmarket.auction.client.ApibaraClient;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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
        return parseLiveLotPages().stream().flatMap(List::stream).toList();
    }

    /** One page per target response, so the scheduler upserts each in its own transaction. */
    @Override
    public List<List<AuctionLot>> parseLiveLotPages() {
        List<List<AuctionLot>> pages = new ArrayList<>();

        if (config.getTargets().isEmpty()) {
            log.warn("No apibara.targets configured — nothing to fetch");
            return pages;
        }

        for (ParserTargetsConfig.Target target : config.getTargets()) {
            List<JsonNode> vehicles =
                client.fetchCopart(target.getMake(), target.getModel(), config.getPerPage());

            List<AuctionLot> page = new ArrayList<>();
            for (JsonNode node : vehicles) {
                ApibaraResponse.Vehicle v = toVehicle(node);
                // API filters make/model; we enforce the year range here
                if (v == null || !target.matchesYear(v.year())) continue;
                AuctionLot lot = toLot(v);
                if (lot != null) {
                    lot.setRawData(node.toString());
                    page.add(lot);
                }
            }
            if (!page.isEmpty()) {
                pages.add(page);
            }
            log.info("Target {} {} [{}-{}]: {} fetched, {} kept after year filter",
                target.getMake(), target.getModel(),
                target.getYearFrom(), target.getYearTo(), vehicles.size(), page.size());
        }
        return pages;
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
        if (v.lotNumber() == null || v.make() == null || v.model() == null || v.year() == null) {
            return null; // required columns — skip incomplete records
        }

        AuctionLot.AuctionLotBuilder b = AuctionLot.builder()
            .vin(v.vin() != null ? v.vin() : "UNKNOWN")
            .make(v.make())
            .model(v.model())
            .year(v.year())
            .source(AuctionLot.AuctionSource.COPART)
            .lotNumber(v.lotNumber())
            .currency("USD")
            .status(mapStatus(v.auction()));

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

    /** Maps Apibara auction state → your LotStatus enum. */
    private AuctionLot.LotStatus mapStatus(ApibaraResponse.Auction auction) {
        if (auction == null || auction.state() == null) {
            return AuctionLot.LotStatus.LIVE;
        }
        return switch (auction.state().toLowerCase()) {
            case "sold" -> AuctionLot.LotStatus.SOLD;
            case "finished", "timed" -> AuctionLot.LotStatus.UNSOLD;
            case "removed" -> AuctionLot.LotStatus.REMOVED;
            default -> AuctionLot.LotStatus.LIVE; // open, upcoming, live
        };
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