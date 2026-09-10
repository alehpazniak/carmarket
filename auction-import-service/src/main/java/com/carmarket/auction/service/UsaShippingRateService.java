package com.carmarket.auction.service;

import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.entity.UsaShippingToPort;
import com.carmarket.auction.repository.UsaShippingToPortRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-first lookup for US auction-to-port shipping prices, backed by {@code usa_shipping_to_port}.
 * <p>
 * When the caller already knows the auction location (e.g. from a synced {@link
 * com.carmarket.auction.entity.AuctionLot#getAuctionLocation()}), a cached rate no older than
 * {@link #MAX_AGE} is served straight from the DB, spending none of Apibara's free-tier quota
 * (100 requests/month). Otherwise — or once the cached rate goes stale — {@code apiFetcher} is
 * called and its result is written back to the cache asynchronously, so the response reaches the
 * frontend without waiting on the DB write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsaShippingRateService {

    private static final Duration MAX_AGE = Duration.ofDays(7);

    private final UsaShippingToPortRepository repository;
    private final UsaShippingRateCacheWriter cacheWriter;
    private final ObjectMapper objectMapper;

    public ApibaraShippingResponse getShipping(String knownLocation, Supplier<ApibaraShippingResponse> apiFetcher) {
        String location = normalize(knownLocation);
        if (location != null) {
            Optional<UsaShippingToPort> cached = repository.findByLocationIgnoreCase(location);
            if (cached.isPresent() && isFresh(cached.get())) {
                log.debug("Serving shipping rate for '{}' from cache (updated_at={})", location, cached.get().getUpdatedAt());
                return toResponse(cached.get());
            }
        }

        ApibaraShippingResponse response = apiFetcher.get();
        cacheWriter.saveAsync(response);
        return response;
    }

    private boolean isFresh(UsaShippingToPort rate) {
        return rate.getUpdatedAt() != null && rate.getUpdatedAt().isAfter(LocalDateTime.now().minus(MAX_AGE));
    }

    private String normalize(String location) {
        return location == null || location.isBlank() ? null : location.trim();
    }

    private ApibaraShippingResponse toResponse(UsaShippingToPort rate) {
        var shipping = new ApibaraShippingResponse.Shipping(
            rate.getRecommendedPort(), rate.getPriceUsd(), rate.getHasShippingPrice(), readPorts(rate.getAvailablePorts()));
        var location = new ApibaraShippingResponse.AuctionLocation(rate.getLocation(), null, null, null);
        var data = new ApibaraShippingResponse.Data(null, location, shipping);
        return new ApibaraShippingResponse(true, data);
    }

    private List<ApibaraShippingResponse.Port> readPorts(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse cached available_ports: {}", e.getMessage());
            return null;
        }
    }
}
