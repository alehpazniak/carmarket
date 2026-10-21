package com.carmarket.auction.service;

import com.carmarket.auction.dto.AuctionLotChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends auction.lot.updated only once the upsert has committed, so a rolled-back page never
 * announces lots that aren't in the database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLotEventPublisher {

    static final String TOPIC_LOT_UPDATED = "auction.lot.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLotChanged(AuctionLotChangedEvent event) {
        try {
            kafkaTemplate.send(TOPIC_LOT_UPDATED, event.id().toString(), event);
        } catch (RuntimeException e) {
            // the lot is already committed; a lost event must not fail the sync
            log.warn("Could not publish {} for lot {} {}: {}", TOPIC_LOT_UPDATED, event.source(), event.lotNumber(),
                e.getMessage());
        }
    }
}
