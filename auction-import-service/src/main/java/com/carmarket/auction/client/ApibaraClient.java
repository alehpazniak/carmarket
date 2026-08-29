package com.carmarket.auction.client;

import com.carmarket.auction.config.ParserTargetsConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
    private static final String MOCK_VEHICLE_DETAIL_RESOURCE = "mock/apibara-vehicle-detail.json";

    private final ParserTargetsConfig config;
    private final ObjectMapper objectMapper;
    private RestClient restClient;
    private ApibaraVehicleDetailResponse mockVehicleDetail;

    @PostConstruct
    void init() {

        this.mockVehicleDetail = loadMockVehicleDetail();
    }

    /**
     * Fetch one page of Copart vehicles for a given make/model.
     * Year filtering is applied by the caller (API filters by make/model/platform).
     */
    public List<ApibaraResponse.Vehicle> fetchCopart(String make, String model, int perPage) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("platform", "copart");
        params.add("make", make);
        params.add("model", model);
        params.add("per_page", String.valueOf(perPage));

        ApibaraResponse response = get(VEHICLES_PATH, params, ApibaraResponse.class);
        return response == null || response.data() == null ? List.of() : response.data();
    }

    /** GET /vehicles — search with arbitrary Apibara filters (make, model, platform, price_min, color[], ...). */
    public ApibaraResponse searchVehicles(MultiValueMap<String, String> filters) {
        return get(VEHICLES_PATH, filters, ApibaraResponse.class);
    }

    /** GET /vehicles/filters — available filter values/ranges for building search UIs. */
    public JsonNode getFiltersMetadata() {
        return get(VEHICLE_FILTERS_PATH, NO_PARAMS, JsonNode.class);
    }

    /**
     * GET /vehicles/{slugVin} — full normalized vehicle record.
     * TEMPORARY: served from a local fixture instead of the real Apibara API, so vehicle-page
     * development doesn't burn the free-tier monthly quota. Swap back to the {@code get(...)}
     * call below once live data is needed again.
     */
    public ApibaraVehicleDetailResponse getVehicle(String slugVin) {
        log.info("Apibara vehicle detail for '{}' served from local mock data — real API call skipped", slugVin);
        return mockVehicleDetail;
    }

    public ApibaraVehicleDetailResponse loadMockVehicleDetail() {
        try {
            return objectMapper.readValue(
                new ClassPathResource(MOCK_VEHICLE_DETAIL_RESOURCE).getInputStream(),
                ApibaraVehicleDetailResponse.class
            );
        } catch (IOException e) {
            log.error("Failed to load mock Apibara vehicle detail fixture '{}': {}", MOCK_VEHICLE_DETAIL_RESOURCE, e.getMessage());
            return null;
        }
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
    public JsonNode resolveFromUrl(String url) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("url", url);
        return get(VEHICLE_URL_TO_DETAILS_PATH, params, JsonNode.class);
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
            return restClient.get()
                .uri(uriBuilder -> uriBuilder.path(IMAGE_PROXY_PATH).queryParam("url", imageUrl).build())
                .retrieve()
                .toEntity(byte[].class);
        } catch (Exception e) {
            log.error("Apibara image proxy failed for {}: {}", imageUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
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
