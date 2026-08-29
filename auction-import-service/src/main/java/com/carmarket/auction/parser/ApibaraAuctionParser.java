package com.carmarket.auction.parser;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.entity.AuctionLot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

    @Override
    public boolean supports(AuctionSource source) {
        return source == AuctionSource.COPART;
    }

    @Override
    public List<AuctionLot> parseLiveLots() {
        List<AuctionLot> result = new ArrayList<>();

        if (config.getTargets().isEmpty()) {
            log.warn("No apibara.targets configured — nothing to fetch");
            return result;
        }

        for (ParserTargetsConfig.Target target : config.getTargets()) {
            List<ApibaraResponse.Vehicle> vehicles =
                client.fetchCopart(target.getMake(), target.getModel(), config.getPerPage());

            int kept = 0;
            for (ApibaraResponse.Vehicle v : vehicles) {
                // API filters make/model; we enforce the year range here
                if (!target.matchesYear(v.year())) continue;
                AuctionLot lot = toLot(v);
                if (lot != null) {
                    result.add(lot);
                    kept++;
                }
            }
            log.info("Target {} {} [{}-{}]: {} fetched, {} kept after year filter",
                target.getMake(), target.getModel(),
                target.getYearFrom(), target.getYearTo(), vehicles.size(), kept);
        }
        return result;
    }

    @Override
    public AuctionLot parseLotDetail(String lotNumber) {
        // Optional: implement a by-lot lookup if you need single-lot refresh.
        // Apibara supports lookup by lot_number; add a client method when needed.
        log.debug("parseLotDetail not implemented for Apibara parser (lot {})", lotNumber);
        return null;
    }

    /** Maps an Apibara vehicle to your AuctionLot entity, guarding every nullable field. */
    private AuctionLot toLot(ApibaraResponse.Vehicle v) {
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

    /** Parses ISO-8601 with offset (e.g. "2026-07-13T14:00:00+00:00") to LocalDateTime. */
    private LocalDateTime parseDate(String iso) {
        try {
            return OffsetDateTime.parse(iso).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.debug("Unparseable auction date: {}", iso);
            return null;
        }
    }
}