package com.carmarket.auction.service;

import com.carmarket.auction.dto.AuctionLotChangedEvent;
import com.carmarket.auction.dto.AuctionLotChangedEvent.ChangeType;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import com.carmarket.auction.sync.LotOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionLotService {

    private final AuctionLotRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** How many lots a sync added and updated (unchanged ones are neither). */
    public record SyncCounts(int added, int updated) {

        public SyncCounts plus(SyncCounts other) {
            return new SyncCounts(added + other.added, updated + other.updated);
        }
    }

    /**
     * Upserts one response page by (source, lot_number) in one transaction. A lot is rewritten only when its
     * content hash changed; auction.lot.updated goes out after commit (AuctionLotEventPublisher). Every lot of
     * the page gets last_seen_at = now, unchanged ones through one UPDATE for the page.
     */
    @Transactional
    public SyncCounts saveOrUpdateLots(List<AuctionLot> lots) {
        Instant now = clock.instant();
        List<UUID> seen = new ArrayList<>();
        int added = 0;
        int updated = 0;
        for (AuctionLot lot : lots) {
            String hash = LotContentHash.of(lot);
            Optional<AuctionLot> existing = repository.findByLotNumberAndSource(
                lot.getLotNumber(), lot.getSource());

            if (existing.isPresent()) {
                AuctionLot current = existing.get();
                seen.add(current.getId());
                if (keepsOutcome(current, lot)) {
                    log.debug("Lot {} {} keeps {}; the feed sent an older {}", current.getSource(),
                        current.getLotNumber(), current.getStatus(), lot.getStatus());
                    continue;
                }
                if (!hash.equals(current.getContentHash())) {
                    if (isFinal(current.getStatus()) && lot.getStatus() == AuctionLot.LotStatus.LIVE) {
                        log.warn("Lot {} {} relisted: {} → LIVE (auction {})", current.getSource(),
                            current.getLotNumber(), current.getStatus(), lot.getAuctionDate());
                    }
                    updateLot(current, lot);
                    current.setContentHash(hash);
                    repository.save(current);
                    events.publishEvent(AuctionLotChangedEvent.of(current, ChangeType.UPDATED));
                    updated++;
                }
            } else {
                lot.setContentHash(hash);
                lot.setLastSeenAt(now);
                AuctionLot saved = repository.save(lot);
                events.publishEvent(AuctionLotChangedEvent.of(saved, ChangeType.CREATED));
                log.info("New lot indexed: {} {}", saved.getSource(), saved.getLotNumber());
                added++;
            }
        }
        if (!seen.isEmpty()) {
            repository.markSeen(seen, now);
        }
        return new SyncCounts(added, updated);
    }

    /**
     * Applies one /changes page of auction results in one transaction, to the lots we store; events for other lots
     * are ignored. A price, sale date or end the event doesn't carry is kept from the lot (e.g. the approval price of
     * a "Sold on Approval" lot). REMOVED never replaces a final result. Returns how many lots changed.
     */
    @Transactional
    public int applyOutcomes(List<LotOutcome> outcomes) {
        int changed = 0;
        for (LotOutcome outcome : outcomes) {
            Optional<AuctionLot> existing = repository.findByLotNumberAndSource(outcome.lotNumber(), outcome.source());
            if (existing.isEmpty()) continue;
            AuctionLot lot = existing.get();
            if (isFinal(lot.getStatus()) && outcome.status() == AuctionLot.LotStatus.REMOVED) continue;

            lot.setStatus(outcome.status());
            if (outcome.status() == AuctionLot.LotStatus.SOLD) {
                if (outcome.price() != null) lot.setSoldPrice(outcome.price());
                if (outcome.saleDate() != null) lot.setSaleDate(outcome.saleDate());
            } else if (outcome.status() == AuctionLot.LotStatus.UNSOLD) {
                lot.setSoldPrice(null);
            }
            if (outcome.endedAt() != null) {
                lot.setEndedAt(outcome.endedAt());
            } else if (lot.getEndedAt() == null) {
                lot.setEndedAt(lot.getAuctionDate());
            }
            String hash = LotContentHash.of(lot);
            if (!hash.equals(lot.getContentHash())) {
                lot.setContentHash(hash);
                repository.save(lot);
                events.publishEvent(AuctionLotChangedEvent.of(lot, ChangeType.UPDATED));
                changed++;
            }
        }
        return changed;
    }

    private static boolean isFinal(AuctionLot.LotStatus status) {
        return status == AuctionLot.LotStatus.SOLD || status == AuctionLot.LotStatus.UNSOLD;
    }

    /**
     * A final result (SOLD / UNSOLD) is replaced only by another final result (a sale falling through) or by a
     * relisting: LIVE with an auction after the one that ended. An older LIVE record, "ended without result"
     * (EXPIRED) or REMOVED leave it. Any status from the feed replaces EXPIRED, the expiry job's fallback.
     */
    private static boolean keepsOutcome(AuctionLot current, AuctionLot incoming) {
        if (!isFinal(current.getStatus()) || isFinal(incoming.getStatus())) return false;
        if (incoming.getStatus() != AuctionLot.LotStatus.LIVE) return true;
        Instant ended = current.getEndedAt() != null ? current.getEndedAt() : current.getAuctionDate();
        boolean relisted = incoming.getAuctionDate() != null && (ended == null || incoming.getAuctionDate().isAfter(ended));
        return !relisted;
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
        target.setVehicleType(source.getVehicleType());
        target.setBodyStyle(source.getBodyStyle());
        target.setSoldPrice(source.getSoldPrice());
        target.setEndedAt(source.getEndedAt());
        target.getImageUrls().clear();
        if (source.getImageUrls() != null) {
            target.getImageUrls().addAll(source.getImageUrls());
        }
        target.setRawData(source.getRawData());
    }
}
