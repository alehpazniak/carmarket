package com.carmarket.auction.client;

import com.carmarket.auction.config.ParserTargetsConfig;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin HTTP client for the Apibara vehicle-auction API.
 * See https://apibara.tech/en/products/vehicle-auction-data-api/docs for the full contract.
 * Free tier is 100 requests/month — every method here consumes one call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApibaraClient {

    private static final String VEHICLES_PATH = "/api/v1/vehicle-auction/vehicles";
    private static final String VEHICLE_FILTERS_PATH = "/api/v1/vehicle-auction/vehicles/filters";
    private static final String VEHICLE_URL_TO_DETAILS_PATH = "/api/v1/vehicle-auction/vehicles/urltodetails";
    private static final String VEHICLE_DETAIL_PATH = "/api/v1/vehicle-auction/vehicles/{slugVin}";
    private static final String VEHICLE_HISTORY_PATH = "/api/v1/vehicle-auction/vehicles/{slugVin}/history";
    private static final String VEHICLE_RELATED_PATH = "/api/v1/vehicle-auction/vehicles/{slugVin}/related";
    private static final String VEHICLE_SHIPPING_PATH = "/api/v1/vehicle-auction/vehicles/{slugVin}/shipping";
    private static final String AUCTION_TO_PORT_SHIPPING_PATH = "/api/v1/vehicle-auction/shipping/auction-to-port";
    private static final String LOCATIONS_PATH = "/api/v1/vehicle-auction/locations";
    private static final String USAGE_PATH = "/api/v1/vehicle-auction/usage";
    private static final String IMAGE_PROXY_PATH = "/api/v1/vehicle-auction/image-proxy";

    private static final MultiValueMap<String, String> NO_PARAMS = new LinkedMultiValueMap<>();

    private static final String PER_PAGE_LIMIT_HEADER = "X-Per-Page-Limit";

    private final ParserTargetsConfig config;
    private final ApibaraQuotaTracker quotaTracker;
    private final Clock clock;
    private RestClient restClient;
    /** Last X-Per-Page-Limit Apibara sent (the plan's maximum page size); 0 until the first response. */
    private final AtomicInteger perPageLimit = new AtomicInteger();

    @PostConstruct
    void init() {
        init(RestClient.builder());
    }

    /** Tests pass a builder bound to MockRestServiceServer. */
    void init(RestClient.Builder builder) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("apibara.api-key is not set — auction sync will be skipped");
        }
        this.restClient = builder
                .baseUrl(config.getBaseUrl())
                .defaultHeader("X-API-Key", config.getApiKey() == null ? "" : config.getApiKey())
                .build();
    }

    /**
     * GET /vehicles for the background sync only (the live lookups keep {@link #searchVehicles}). Reads the
     * quota headers of every answer into the {@link ApibaraQuotaTracker} and throws instead of returning null:
     * {@link ApibaraQuotaExceededException} on 429, {@link ApibaraUnavailableException} on anything else.
     * Each vehicle comes back as raw JSON so the caller can keep it.
     */
    public ApibaraVehiclesPage searchVehiclesPage(MultiValueMap<String, String> params) {
        if (apiKeyMissing()) {
            throw new ApibaraUnavailableException("apibara.api-key is not set");
        }
        Exchange answer;
        quotaTracker.requestSent();
        try {
            answer = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(VEHICLES_PATH).queryParams(params).build())
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    JsonNode body = status.is2xxSuccessful() ? response.bodyTo(JsonNode.class) : null;
                    HttpHeaders headers = new HttpHeaders();
                    headers.putAll(response.getHeaders());
                    return new Exchange(status, headers, body);
                }, true);
        } catch (Exception e) {
            // the message names the URL and the cause, never the X-API-Key header
            throw new ApibaraUnavailableException("Apibara request failed: " + e.getMessage(), e);
        }

        ApibaraQuota quota = ApibaraQuota.from(answer.headers(), clock.instant());
        quotaTracker.record(quota);
        // the header comes on error responses too, e.g. a 4xx for a per_page above the limit
        rememberPerPageLimit(answer.headers().getFirst(PER_PAGE_LIMIT_HEADER));

        if (answer.status().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            Duration retryAfter = retryAfter(answer.headers().getFirst(HttpHeaders.RETRY_AFTER), clock.instant());
            quotaTracker.quotaExhausted(retryAfter != null ? clock.instant().plus(retryAfter) : null);
            throw new ApibaraQuotaExceededException(retryAfter, quota);
        }
        if (!answer.status().is2xxSuccessful()) {
            throw new ApibaraUnavailableException("Apibara answered HTTP " + answer.status().value());
        }
        JsonNode data = answer.body() == null ? null : answer.body().get("data");
        if (data == null || !data.isArray()) {
            throw new ApibaraUnavailableException("Apibara answered without a data array");
        }
        List<JsonNode> vehicles = new ArrayList<>(data.size());
        data.forEach(vehicles::add);
        JsonNode meta = answer.body().path("meta");
        String nextCursor = meta.path("next_cursor").isTextual() ? meta.path("next_cursor").asText() : null;
        Integer perPage = meta.path("per_page").isInt() ? meta.path("per_page").asInt() : null;
        return new ApibaraVehiclesPage(vehicles, new ApibaraResponse.Meta(nextCursor, perPage), quota);
    }

    private record Exchange(HttpStatusCode status, HttpHeaders headers, JsonNode body) {
    }

    /** Retry-After is seconds per Apibara's OpenAPI; an HTTP date is accepted too. Null when absent or unreadable. */
    static Duration retryAfter(String header, Instant now) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(header.trim())));
        } catch (NumberFormatException e) {
            try {
                Instant at = ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return at.isAfter(now) ? Duration.between(now, at) : Duration.ZERO;
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    /** The configured page size, capped by the plan's X-Per-Page-Limit once Apibara has sent it. */
    public int effectivePerPage(int configured) {
        int limit = perPageLimit.get();
        return limit > 0 ? Math.min(configured, limit) : configured;
    }

    void rememberPerPageLimit(String header) {
        if (header == null || header.isBlank()) {
            return;
        }
        try {
            int limit = Integer.parseInt(header.trim());
            if (limit > 0 && perPageLimit.getAndSet(limit) != limit) {
                log.info("Apibara per-page limit is {}", limit);
            }
        } catch (NumberFormatException e) {
            log.debug("Unparseable {} header: {}", PER_PAGE_LIMIT_HEADER, header);
        }
    }

    /** GET /vehicles — search with arbitrary Apibara filters (make, model, platform, price_min, color[], ...). */
    public ApibaraResponse searchVehicles(MultiValueMap<String, String> filters) {
        return get(VEHICLES_PATH, filters, ApibaraResponse.class);
    }

    /** GET /vehicles/filters — available filter values/ranges for building search UIs. */
    public JsonNode getFiltersMetadata() {
        return get(VEHICLE_FILTERS_PATH, NO_PARAMS, JsonNode.class);
    }

    /** GET /vehicles/{slugVin} — full normalized vehicle record. */
    public ApibaraVehicleDetailResponse getVehicle(String slugVin) {
        return get(VEHICLE_DETAIL_PATH, Map.of("slugVin", slugVin), NO_PARAMS, ApibaraVehicleDetailResponse.class);
    }

    /** GET /vehicles/{slugVin}/history — paginated past auction/sale records for the vehicle. */
    public JsonNode getVehicleHistory(String slugVin, MultiValueMap<String, String> params) {
        return get(VEHICLE_HISTORY_PATH, Map.of("slugVin", slugVin), params, JsonNode.class);
    }

    /** GET /vehicles/{slugVin}/related — similar/related vehicles (source, upcoming, past). */
    public JsonNode getRelatedVehicles(String slugVin) {
        return get(VEHICLE_RELATED_PATH, Map.of("slugVin", slugVin), NO_PARAMS, JsonNode.class);
    }

    /** GET /vehicles/{slugVin}/shipping — auction-to-port shipping for a known vehicle. */
    public ApibaraShippingResponse getVehicleShipping(String slugVin, String ports) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (ports != null && !ports.isBlank()) params.add("ports", ports);
        return get(VEHICLE_SHIPPING_PATH, Map.of("slugVin", slugVin), params, ApibaraShippingResponse.class);
    }

    /** GET /shipping/auction-to-port — shipping estimate by VIN/lot number without fetching the full vehicle. */
    public ApibaraShippingResponse getAuctionToPortShipping(String vin, String lotNumber, String ports) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (vin != null && !vin.isBlank()) params.add("vin", vin);
        if (lotNumber != null && !lotNumber.isBlank()) params.add("lot_number", lotNumber);
        if (ports != null && !ports.isBlank()) params.add("ports", ports);
        return get(AUCTION_TO_PORT_SHIPPING_PATH, params, ApibaraShippingResponse.class);
    }

    /** GET /locations — auction facilities/offices, optionally filtered (platform, state, zip, radius, ...). */
    public JsonNode getLocations(MultiValueMap<String, String> filters) {
        return get(LOCATIONS_PATH, filters, JsonNode.class);
    }

    /** GET /vehicles/urltodetails — resolve a Copart/IAAI listing URL to a normalized vehicle record. */
    public ApibaraVehicleDetailResponse resolveFromUrl(String url) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("url", url);
        return get(VEHICLE_URL_TO_DETAILS_PATH, params, ApibaraVehicleDetailResponse.class);
    }

    /** GET /usage — API key usage stats and plan limits. */
    public JsonNode getUsage() {
        return get(USAGE_PATH, NO_PARAMS, JsonNode.class);
    }

    /** GET /image-proxy — streams an auction image through Apibara (bypasses source hotlink protection). */
    public ResponseEntity<byte[]> proxyImage(String imageUrl) {
        if (apiKeyMissing()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        try {
            ResponseEntity<byte[]> upstream = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(IMAGE_PROXY_PATH).queryParam("url", imageUrl).build())
                .retrieve()
                .toEntity(byte[].class);
            // Only the content headers: passing Apibara's on (e.g. "Access-Control-Allow-Origin: *",
            // Cloudflare headers) would give browsers a second, conflicting CORS header next to the gateway's.
            HttpHeaders headers = new HttpHeaders();
            if (upstream.getHeaders().getContentType() != null) {
                headers.setContentType(upstream.getHeaders().getContentType());
            }
            String cacheControl = upstream.getHeaders().getCacheControl();
            if (cacheControl != null) {
                headers.setCacheControl(cacheControl);
            }
            return new ResponseEntity<>(upstream.getBody(), headers, upstream.getStatusCode());
        } catch (Exception e) {
            log.error("Apibara image proxy failed for {}: {}", imageUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /** Whether apibara.api-key is set; without it every call returns null and the sync is skipped. */
    public boolean isConfigured() {
        return !apiKeyMissing();
    }

    private boolean apiKeyMissing() {
        return config.getApiKey() == null || config.getApiKey().isBlank();
    }

    private <T> T get(String path, MultiValueMap<String, String> params, Class<T> type) {
        return get(path, Map.of(), params, type);
    }

    private <T> T get(String path, Map<String, ?> pathVars, MultiValueMap<String, String> params, Class<T> type) {
        if (apiKeyMissing()) {
            return null;
        }
        try {
            T body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParams(params).build(pathVars))
                    .retrieve()
                    .body(type);
            return body;
        } catch (Exception e) {
            log.error("Apibara request failed for {}: {}", path, e.getMessage());
            return null;
        }
    }
}
