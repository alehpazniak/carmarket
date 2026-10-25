package com.carmarket.auction.service;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraVehicleDetailResponse;
import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.parser.ApibaraAuctionParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The quota-metered live vehicle lookups (GET /apibara/vehicles/{slugVin}, /apibara/vehicles/url-to-details):
 * <ul>
 *   <li>answers are kept in memory for apibara.live-lookup.cache-minutes under the normalized identifier, and under
 *   the found car's VIN, lot number and slug, so opening the page after resolving a link, or again, costs nothing;
 *   "not found" and failures aren't cached;</li>
 *   <li>a car that passes the sync's filters (vehicle types, Run and Drive, title) is upserted into auction_lots
 *   like a sync page, so the next lookup finds it in our database and it shows in the listing. Others are only
 *   shown.</li>
 * </ul>
 * Failures propagate from {@link ApibaraClient#lookupVehicle}: ApibaraQuotaExceededException (429) and
 * ApibaraUnavailableException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveVehicleLookup {

    private static final int MAX_ENTRIES = 1000;

    private final ApibaraClient client;
    private final ApibaraVehicleResolver resolver;
    private final ApibaraAuctionParser mapper;
    private final AuctionLotService lotService;
    private final ParserTargetsConfig config;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(ApibaraVehicleDetailResponse response, Instant expiresAt) {
    }

    /** A VIN, lot number or Apibara slug; empty when Apibara doesn't know it. */
    public Optional<ApibaraVehicleDetailResponse> vehicle(String slugVin) {
        String value = slugVin.trim();
        return cachedOrFetch(key(value), () -> client.lookupVehicle(value));
    }

    /** A pasted Copart/IAAI link, lot number or VIN, through {@link ApibaraVehicleResolver}. */
    public Optional<ApibaraVehicleDetailResponse> resolve(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return cachedOrFetch(key(value), () -> resolver.resolve(value));
    }

    /** Trimmed, upper case: a VIN or lot number is the same whatever its case, and so is a link's lot. */
    static String key(String identifier) {
        return identifier.trim().toUpperCase(Locale.ROOT);
    }

    private Optional<ApibaraVehicleDetailResponse> cachedOrFetch(String key, Supplier<Optional<JsonNode>> fetch) {
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && now.isBefore(hit.expiresAt())) {
            return Optional.of(hit.response());
        }
        Optional<JsonNode> found = fetch.get();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ApibaraVehicleDetailResponse response = toResponse(found.get());
        if (response == null || response.data() == null) {
            return Optional.empty();
        }
        remember(key, response, now);
        store(found.get().get("data"));
        return Optional.of(response);
    }

    private void remember(String key, ApibaraVehicleDetailResponse response, Instant now) {
        Duration ttl = Duration.ofMinutes(config.getLiveLookup().getCacheMinutes());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.values().removeIf(c -> !now.isBefore(c.expiresAt()));
        }
        Cached entry = new Cached(response, now.plus(ttl));
        cache.put(key, entry);
        ApibaraVehicleDetailResponse.Vehicle v = response.data();
        for (String alias : new String[]{v.vin(), v.lotNumber(), v.slugVin()}) {
            if (alias != null && !alias.isBlank() && !alias.contains("*")) { // a masked VIN is no identifier
                cache.put(key(alias), entry);
            }
        }
    }

    /** Upserts the car when it passes the sync's filters; a failure here never fails the lookup. */
    private void store(JsonNode vehicle) {
        try {
            ApibaraAuctionParser.MappedPage mapped = mapper.map(List.of(vehicle));
            if (mapped.lots().isEmpty()) {
                log.debug("Live lookup {} not stored (skipped_type={}, skipped_condition={}, skipped_title={})",
                    vehicle.path("lot_number").asText(), mapped.skippedType(), mapped.skippedCondition(), mapped.skippedTitle());
                return;
            }
            lotService.saveOrUpdateLots(mapped.lots());
        } catch (RuntimeException e) {
            log.warn("Storing live lookup {} failed: {}", vehicle.path("lot_number").asText(), e.getMessage());
        }
    }

    private ApibaraVehicleDetailResponse toResponse(JsonNode body) {
        try {
            return objectMapper.treeToValue(body, ApibaraVehicleDetailResponse.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Unreadable Apibara vehicle answer: {}", e.getMessage());
            return null;
        }
    }
}
