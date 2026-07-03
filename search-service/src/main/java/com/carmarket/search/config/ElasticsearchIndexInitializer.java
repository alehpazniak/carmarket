package com.carmarket.search.config;

import com.carmarket.search.document.CarDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Ensures the car_listings index exists on startup.
 * Without this, searching an empty/rebuilt Elasticsearch throws NoSuchIndexException.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexIfMissing() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(CarDocument.class);
        if (indexOps.exists()) {
            log.info("Elasticsearch index 'car_listings' already exists");
            return;
        }
        boolean created = indexOps.createWithMapping(); // creates index + applies @Field mappings from CarDocument
        if (created) {
            log.info("Created Elasticsearch index 'car_listings' with mapping");
        } else {
            log.warn("Failed to create Elasticsearch index 'car_listings'");
        }
    }
}
