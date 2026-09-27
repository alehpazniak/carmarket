package com.carmarket.car.kafka;

import com.carmarket.car.dto.CarUpdatedEvent;
import com.carmarket.car.entity.CarListing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Publishes car listing events to Kafka.
 * search-service consumes these to keep Elasticsearch in sync.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarEventProducer {

    private static final String TOPIC_CAR_CREATED = "car.created";
    private static final String TOPIC_CAR_UPDATED = "car.updated";
    private static final String TOPIC_CAR_DELETED = "car.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCreated(CarListing car) {
        send(TOPIC_CAR_CREATED, car.getId().toString(), toEvent(car));
    }

    public void publishUpdated(CarListing car) {
        send(TOPIC_CAR_UPDATED, car.getId().toString(), toEvent(car));
    }

    public void publishDeleted(String carId) {
        send(TOPIC_CAR_DELETED, carId, Map.of("carId", carId));
    }

    /**
     * Inside a transaction the event is sent only after commit — otherwise a rolled-back
     * write would still reach search-service and leave a ghost listing in Elasticsearch.
     * The payload is built by the caller before this point, so it reflects the state being committed.
     */
    private void send(String topic, String carId, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doSend(topic, carId, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doSend(topic, carId, payload);
            }
        });
    }

    private void doSend(String topic, String carId, Object payload) {
        kafkaTemplate.send(topic, carId, payload);
        log.info("Published {} for carId: {}", topic, carId);
    }

    private CarUpdatedEvent toEvent(CarListing car) {
        return new CarUpdatedEvent(
            car.getId().toString(),
            car.getSellerId().toString(),
            car.getMake(),
            car.getModel(),
            car.getYear(),
            car.getPrice(),
            car.getMileage(),
            car.getFuelType() != null ? car.getFuelType().name() : null,
            car.getTransmission() != null ? car.getTransmission().name() : null,
            car.getCity(),
            car.getStatus().name(),
            car.getPrimaryImageUrl(),
            car.getCreatedAt(),
            car.getCategory() != null ? car.getCategory().name() : null
        );
    }
}
