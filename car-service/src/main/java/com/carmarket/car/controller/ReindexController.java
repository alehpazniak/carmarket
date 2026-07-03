package com.carmarket.car.controller;

import com.carmarket.car.entity.CarListing;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.repository.CarListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin: rebuild the Elasticsearch index from PostgreSQL (the source of truth).
 * Re-publishes car.created for every listing; search-service re-indexes them (upsert by id).
 * <p>
 * Call after wiping/rebuilding Elasticsearch: POST /api/cars/admin/reindex
 */
@Slf4j
@RestController
@RequestMapping("/cars/admin")
@RequiredArgsConstructor
public class ReindexController {

    private static final int BATCH_SIZE = 200;

    private final CarListingRepository carListingRepository;
    private final CarEventProducer eventProducer;

    @PostMapping("/reindex")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> reindex() {
        long total = 0;
        int pageNumber = 0;
        Page<CarListing> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, BATCH_SIZE);
            page = carListingRepository.findAll(pageable);
            for (CarListing car : page.getContent()) {
                eventProducer.publishCreated(car);
                total++;
            }
            pageNumber++;
        } while (page.hasNext());

        log.info("Reindex triggered: re-published {} car listings to Kafka", total);
        return ResponseEntity.ok(Map.of(
            "status", "reindex triggered",
            "published", total
        ));
    }
}
