package com.carmarket.search.kafka;

import com.carmarket.search.document.CarDocument;
import com.carmarket.search.dto.CarUpdatedEvent;
import com.carmarket.search.service.CarSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Consumes car.created, car.updated, car.deleted from Kafka
 * and keeps Elasticsearch in sync with PostgreSQL (car-service).
 * <p>
 * This is the core of the CQRS pattern:
 * car-service  →  Kafka  →  search-service  →  Elasticsearch
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarIndexingConsumer {

    private final CarSearchService searchService;

    @KafkaListener(topics = "car.created", groupId = "search-service")
    public void handleCarCreated(CarUpdatedEvent event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received car.created event for carId: {}", key);
        CarDocument doc = toDocument(event);
        searchService.indexCar(doc);
    }

    @KafkaListener(topics = "car.updated", groupId = "search-service")
    public void handleCarUpdated(CarUpdatedEvent event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received car.updated event for carId: {}", key);
        CarDocument doc = toDocument(event);
        searchService.indexCar(doc);
    }

    @KafkaListener(topics = "car.deleted", groupId = "search-service")
    public void handleCarDeleted(CarUpdatedEvent event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received car.deleted event for carId: {}", key);
        String carId = event.carId();
        searchService.removeCar(carId);
    }

    private CarDocument toDocument(CarUpdatedEvent event) {
        return CarDocument.builder()
            .id(event.carId())
            .sellerId(event.sellerId())
            .make(event.make())
            .model(event.model())
            .year(event.year())
            .price(event.price())
            .mileage(event.mileage())
            .fuelType(event.fuelType())
            .transmission(event.transmission())
            .city(event.city())
            .status(event.status())
            .createdAt(event.createdAt() != null
                    ? event.createdAt().atZone(ZoneId.systemDefault()).toInstant()
                    : Instant.now())
            .build();
    }
}
