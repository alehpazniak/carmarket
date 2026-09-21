package com.carmarket.auction.service;

import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.entity.UsaShippingToPort;
import com.carmarket.auction.repository.UsaShippingToPortRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Upserts a fresh Apibara shipping quote into the {@code usa_shipping_to_port} cache in the
 * background, so callers returning the quote to the frontend don't wait on the DB write.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsaShippingRateCacheWriter {

    private final UsaShippingToPortRepository repository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void saveAsync(ApibaraShippingResponse response) {
        if (response == null || !response.ok() || response.data() == null) {
            return;
        }
        var location = response.data().auctionLocation();
        var shipping = response.data().shipping();
        if (location == null || location.display() == null || location.display().isBlank() || shipping == null) {
            return;
        }

        String key = location.display().trim();
        try {
            UsaShippingToPort rate = repository.findByLocationIgnoreCase(key).orElseGet(UsaShippingToPort::new);
            rate.setLocation(key);
            rate.setRecommendedPort(shipping.recommendedPort());
            rate.setPriceUsd(shipping.recommendedPriceUsd());
            rate.setHasShippingPrice(shipping.hasShippingPrice());
            rate.setAvailablePorts(writeJson(shipping.availablePorts()));
            rate.setUpdatedAt(LocalDateTime.now());
            repository.save(rate);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent fetch for the same location just inserted first — the cache is
            // already fresh, so there's nothing to reconcile.
            log.debug("Concurrent shipping rate cache write for '{}', ignoring", key);
        }
    }

    private String writeJson(List<ApibaraShippingResponse.Port> ports) {
        if (ports == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ports);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize available_ports for cache: {}", e.getMessage());
            return null;
        }
    }
}
