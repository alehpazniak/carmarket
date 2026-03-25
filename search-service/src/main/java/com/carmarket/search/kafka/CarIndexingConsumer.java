package com.carmarket.search.kafka;

import com.carmarket.search.document.CarDocument;
import com.carmarket.search.service.CarSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

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
    public void handleCarCreated(Map<String, Object> event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received car.created event for carId: {}", key);
        CarDocument doc = toDocument(event);
        searchService.indexCar(doc);
    }

    @KafkaListener(topics = "car.updated", groupId = "search-service")
    public void handleCarUpdated(Map<String, Object> event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received car.updated event for carId: {}", key);
        CarDocument doc = toDocument(event);
        searchService.indexCar(doc);
    }

    @KafkaListener(topics = "car.deleted", groupId = "search-service")
    public void handleCarDeleted(Map<String, Object> event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received car.deleted event for carId: {}", key);
        String carId = (String) event.get("carId");
        searchService.removeCar(carId);
    }

    private CarDocument toDocument(Map<String, Object> event) {
        return CarDocument.builder()
            .id((String) event.get("carId"))
            .sellerId((String) event.get("sellerId"))
            .make((String) event.get("make"))
            .model((String) event.get("model"))
            .year(toInt(event.get("year")))
            .price(toBigDecimal(event.get("price")))
            .mileage(toInt(event.get("mileage")))
            .fuelType((String) event.get("fuelType"))
            .transmission((String) event.get("transmission"))
            .city((String) event.get("city"))
            .status((String) event.get("status"))
            .createdAt(Instant.now())
            .build();
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }
}
