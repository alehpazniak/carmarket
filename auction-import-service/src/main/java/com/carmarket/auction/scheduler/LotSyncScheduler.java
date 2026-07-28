package com.carmarket.auction.scheduler;

import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.parser.AuctionParser;
import com.carmarket.auction.parser.AuctionParser.AuctionSource;
import com.carmarket.auction.service.AuctionLotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LotSyncScheduler {

    private final List<AuctionParser> parsers;
    private final AuctionLotService lotService;

    @Scheduled(fixedDelayString = "${parser.sync.interval:300000}") // default 5 min
    public void syncAllSources() {
        for (AuctionParser parser : parsers) {
            try {
                log.info("Starting sync for {}", parser.getClass().getSimpleName());
                List<AuctionLot> lots = parser.parseLiveLots();
                lotService.saveOrUpdateLots(lots);
                log.info("Synced {} lots from {}", lots.size(), parser.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Sync failed for {}", parser.getClass().getSimpleName(), e);
            }
        }
    }
}
