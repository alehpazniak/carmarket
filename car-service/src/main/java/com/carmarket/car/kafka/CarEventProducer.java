package com.carmarket.car.kafka;

import com.carmarket.car.entity.CarListing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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

    private Map<String, Object> toEvent(CarListing car) {
        Map<String, Object> event = new HashMap<>();
        event.put("carId", car.getId().toString());
        event.put("sellerId", car.getSellerId().toString());
        event.put("make", car.getMake());
        event.put("model", car.getModel());
        event.put("year", car.getYear());
        event.put("price", car.getPrice());
        event.put("mileage", car.getMileage() != null ? car.getMileage() : 0);
        event.put("fuelType", car.getFuelType() != null ? car.getFuelType().name() : "");
        event.put("transmission", car.getTransmission() != null ? car.getTransmission().name() : "");
        event.put("color", car.getColor() != null ? car.getColor() : "");
        event.put("city", car.getCity() != null ? car.getCity() : "");
        event.put("country", car.getCountry() != null ? car.getCountry() : "");
        event.put("description", car.getDescription() != null ? car.getDescription() : "");
        event.put("status", car.getStatus().name());
        return event;
    }
}
