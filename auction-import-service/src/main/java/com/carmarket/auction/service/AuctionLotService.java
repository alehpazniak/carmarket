package com.carmarket.auction.service;

import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionLotService {

    private final AuctionLotRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC_LOT_UPDATED = "auction.lot.updated";

    @Transactional
    public void saveOrUpdateLots(List<AuctionLot> lots) {
        for (AuctionLot lot : lots) {
            Optional<AuctionLot> existing = repository.findByLotNumberAndSource(
                lot.getLotNumber(), lot.getSource());

            if (existing.isPresent()) {
                AuctionLot current = existing.get();
                // Only update if price changed or status changed
                if (hasSignificantChanges(current, lot)) {
                    updateLot(current, lot);
                    repository.save(current);
                    kafkaTemplate.send(TOPIC_LOT_UPDATED, current.getId().toString(), current);
                }
            } else {
                AuctionLot saved = repository.save(lot);
                kafkaTemplate.send(TOPIC_LOT_UPDATED, saved.getId().toString(), saved);
                log.info("New lot indexed: {} {}", saved.getSource(), saved.getLotNumber());
            }
        }
    }

    private boolean hasSignificantChanges(AuctionLot existing, AuctionLot incoming) {
        return existing.getAuctionPrice() == null ||
            !existing.getAuctionPrice().equals(incoming.getAuctionPrice()) ||
            existing.getStatus() != incoming.getStatus();
    }

    private void updateLot(AuctionLot target, AuctionLot source) {
        target.setAuctionPrice(source.getAuctionPrice());
        target.setBuyNowPrice(source.getBuyNowPrice());
        target.setStatus(source.getStatus());
        target.setOdometer(source.getOdometer());
        target.setAuctionLocation(source.getAuctionLocation());
        target.setImageUrls(source.getImageUrls());
    }
}
