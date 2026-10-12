package com.carmarket.car.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * listing.moderated → notification-service emails the seller (LISTING_BLOCKED / LISTING_RESTORED).
 * Keep in sync with notification-service's ListingModeratedConsumer. Sent after commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListingModeratedProducer {

    static final String TOPIC = "listing.moderated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record ListingModeratedEvent(String carId, String sellerId, String action, String reason, String title,
                                        Instant occurredAt) {
    }

    public void publish(ListingModeratedEvent event) {
        Runnable send = () -> {
            kafkaTemplate.send(TOPIC, event.carId(), event);
            log.info("Published {} {} for carId: {}", TOPIC, event.action(), event.carId());
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
