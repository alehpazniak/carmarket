package com.carmarket.auction.client;

import com.carmarket.auction.config.ParserTargetsConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Thin HTTP client for the Apibara vehicle-auction API. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApibaraClient {

    private static final String VEHICLES_PATH = "/api/v1/vehicle-auction/vehicles";

    private final ParserTargetsConfig config;
    private RestClient restClient;

    @PostConstruct
    void init() {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("apibara.api-key is not set — auction sync will be skipped");
        }
        this.restClient = RestClient.builder()
            .baseUrl(config.getBaseUrl())
            .defaultHeader("X-API-Key", config.getApiKey() == null ? "" : config.getApiKey())
            .build();
    }

    /**
     * Fetch one page of Copart vehicles for a given make/model.
     * Year filtering is applied by the caller (API filters by make/model/platform).
     */
    public List<ApibaraResponse.Vehicle> fetchCopart(String make, String model, int perPage) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            return List.of();
        }
        try {
            ApibaraResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(VEHICLES_PATH)
                    .queryParam("platform", "copart")
                    .queryParam("make", make)
                    .queryParam("model", model)
                    .queryParam("per_page", perPage)
                    .build())
                .retrieve()
                .body(ApibaraResponse.class);

            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data();
        } catch (Exception e) {
            log.error("Apibara request failed for {} {}: {}", make, model, e.getMessage());
            return List.of();
        }
    }
}