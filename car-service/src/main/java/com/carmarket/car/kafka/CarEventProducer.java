package com.carmarket.car.kafka;

import com.carmarket.car.dto.CarUpdatedEvent;
import com.carmarket.car.entity.CarListing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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
        kafkaTemplate.send(TOPIC_CAR_CREATED, car.getId().toString(), toEvent(car));
        log.info("Published car.created for carId: {}", car.getId());
    }

    public void publishUpdated(CarListing car) {
        kafkaTemplate.send(TOPIC_CAR_UPDATED, car.getId().toString(), toEvent(car));
        log.info("Published car.updated for carId: {}", car.getId());
    }

    public void publishDeleted(String carId) {
        kafkaTemplate.send(TOPIC_CAR_DELETED, carId, Map.of("carId", carId));
        log.info("Published car.deleted for carId: {}", carId);
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
            car.getCreatedAt()
        );
    }
}
