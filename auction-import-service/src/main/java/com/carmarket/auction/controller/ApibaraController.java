package com.carmarket.auction.controller;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.client.ApibaraVehicleDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Direct passthrough to the Apibara vehicle-auction API
 * (https://apibara.tech/en/products/vehicle-auction-data-api/docs) for ad-hoc lookups —
 * separate from {@link AuctionLotController}, which serves lots already synced into our own DB.
 * Free tier is 100 requests/month, so every call here consumes one — don't poll these from the UI.
 */
@RestController
@RequestMapping("/api/auctions/apibara")
@RequiredArgsConstructor
public class ApibaraController {

    private static final Logger log = LoggerFactory.getLogger(ApibaraController.class);
    private final ApibaraClient client;

    @GetMapping("/vehicles/filters")
    public ResponseEntity<JsonNode> getFilters() {
        return okOrBadGateway(client.getFiltersMetadata());
    }

    @GetMapping("/vehicles/url-to-details")
    public ResponseEntity<JsonNode> resolveFromUrl(@RequestParam String url) {
        return okOrBadGateway(client.resolveFromUrl(url));
    }

    @GetMapping("/vehicles")
    public ResponseEntity<ApibaraResponse> searchVehicles(@RequestParam MultiValueMap<String, String> query) {
        return okOrBadGateway(client.searchVehicles(query));
    }

    @GetMapping("/vehicles/{slugVin}")
    public ResponseEntity<ApibaraVehicleDetailResponse> getVehicle(@PathVariable String slugVin) {
        ApibaraVehicleDetailResponse vehicle = client.loadMockVehicleDetail();
        log.info("vehicle={}",vehicle);
        System.out.println(vehicle);
         return okOrBadGateway(vehicle);
    }

    @GetMapping("/vehicles/{slugVin}/history")
    public ResponseEntity<JsonNode> getVehicleHistory(
        @PathVariable String slugVin,
        @RequestParam MultiValueMap<String, String> query) {
        return okOrBadGateway(client.getVehicleHistory(slugVin, query));
    }

    @GetMapping("/vehicles/{slugVin}/related")
    public ResponseEntity<JsonNode> getRelatedVehicles(@PathVariable String slugVin) {
        return okOrBadGateway(client.getRelatedVehicles(slugVin));
    }

    @GetMapping("/vehicles/{slugVin}/shipping")
    public ResponseEntity<ApibaraShippingResponse> getVehicleShipping(
        @PathVariable String slugVin,
        @RequestParam(required = false) String ports) {
        return okOrBadGateway(client.getVehicleShipping(slugVin, ports));
    }

    @GetMapping("/shipping/auction-to-port")
    public ResponseEntity<ApibaraShippingResponse> getAuctionToPortShipping(
        @RequestParam(required = false) String vin,
        @RequestParam(name = "lot_number", required = false) String lotNumber,
        @RequestParam(required = false) String ports) {
        return okOrBadGateway(client.getAuctionToPortShipping(vin, lotNumber, ports));
    }

    @GetMapping("/locations")
    public ResponseEntity<JsonNode> getLocations(@RequestParam MultiValueMap<String, String> query) {
        return okOrBadGateway(client.getLocations(query));
    }

    @GetMapping("/usage")
    public ResponseEntity<JsonNode> getUsage() {
        return okOrBadGateway(client.getUsage());
    }

    @GetMapping("/image-proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        return client.proxyImage(url);
    }

    private <T> ResponseEntity<T> okOrBadGateway(T body) {
        return body == null ? ResponseEntity.status(HttpStatus.BAD_GATEWAY).build() : ResponseEntity.ok(body);
    }
}
