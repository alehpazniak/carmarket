package com.carmarket.auction.service;

import com.carmarket.auction.dto.AuctionLotChangedEvent;
import com.carmarket.auction.dto.AuctionLotChangedEvent.ChangeType;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionLotService {

    private final AuctionLotRepository repository;
    private final ApplicationEventPublisher events;

    /** How many lots a sync added and updated (unchanged ones are neither). */
    public record SyncCounts(int added, int updated) {

        public SyncCounts plus(SyncCounts other) {
            return new SyncCounts(added + other.added, updated + other.updated);
        }
    }

    /**
     * Upserts one response page by (source, lot_number) in one transaction. A lot is rewritten only when its
     * content hash changed; auction.lot.updated goes out after commit (AuctionLotEventPublisher).
     */
    @Transactional
    public SyncCounts saveOrUpdateLots(List<AuctionLot> lots) {
        int added = 0;
        int updated = 0;
        for (AuctionLot lot : lots) {
            String hash = LotContentHash.of(lot);
            Optional<AuctionLot> existing = repository.findByLotNumberAndSource(
                lot.getLotNumber(), lot.getSource());

            if (existing.isPresent()) {
                AuctionLot current = existing.get();
                if (!hash.equals(current.getContentHash())) {
                    updateLot(current, lot);
                    current.setContentHash(hash);
                    repository.save(current);
                    events.publishEvent(AuctionLotChangedEvent.of(current, ChangeType.UPDATED));
                    updated++;
                }
            } else {
                lot.setContentHash(hash);
                AuctionLot saved = repository.save(lot);
                events.publishEvent(AuctionLotChangedEvent.of(saved, ChangeType.CREATED));
                log.info("New lot indexed: {} {}", saved.getSource(), saved.getLotNumber());
                added++;
            }
        }
        return new SyncCounts(added, updated);
    }

    /** Copies every field a parser maps (the ones LotContentHash covers) plus the raw JSON. */
    private void updateLot(AuctionLot target, AuctionLot source) {
        target.setVin(source.getVin());
        target.setMake(source.getMake());
        target.setModel(source.getModel());
        target.setYear(source.getYear());
        target.setAuctionPrice(source.getAuctionPrice());
        target.setBuyNowPrice(source.getBuyNowPrice());
        target.setCurrency(source.getCurrency());
        target.setDamageType(source.getDamageType());
        target.setPrimaryDamage(source.getPrimaryDamage());
        target.setSecondaryDamage(source.getSecondaryDamage());
        target.setOdometer(source.getOdometer());
        target.setOdometerUnit(source.getOdometerUnit());
        target.setEngineCapacity(source.getEngineCapacity());
        target.setFuelType(source.getFuelType());
        target.setTransmission(source.getTransmission());
        target.setAuctionLocation(source.getAuctionLocation());
        target.setAuctionDate(source.getAuctionDate());
        target.setSaleDate(source.getSaleDate());
        target.setStatus(source.getStatus());
        target.getImageUrls().clear();
        if (source.getImageUrls() != null) {
            target.getImageUrls().addAll(source.getImageUrls());
        }
        target.setRawData(source.getRawData());
    }
}
