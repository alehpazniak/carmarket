package com.carmarket.auction.controller;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.client.ApibaraVehicleDetailResponse;
import com.carmarket.auction.dto.MaxBidRequest;
import com.carmarket.auction.dto.MaxBidResponse;
import com.carmarket.auction.service.UsaShippingRateService;
import com.carmarket.auction.service.calculator.MaxBidCalculator;
import com.carmarket.auction.service.calculator.MaxBidInput;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

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
    private final MaxBidCalculator maxBidCalculator;
    private final UsaShippingRateService shippingRateService;

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
        return okOrBadGateway(client.getVehicle(slugVin));
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
        return okOrBadGateway(shippingRateService.getShipping(null, () -> client.getVehicleShipping(slugVin, ports)));
    }

    @GetMapping("/shipping/auction-to-port")
    public ResponseEntity<ApibaraShippingResponse> getAuctionToPortShipping(
        @RequestParam(required = false) String vin,
        @RequestParam(name = "lot_number", required = false) String lotNumber,
        @RequestParam(required = false) String ports) {
        return okOrBadGateway(shippingRateService.getShipping(null, () -> client.getAuctionToPortShipping(vin, lotNumber, ports)));
    }

    @PostMapping("/vehicles/{slugVin}/max-bid")
    public ResponseEntity<MaxBidResponse> calculateMaxBid(
        @PathVariable String slugVin,
        @RequestBody MaxBidRequest request) {

        BigDecimal shippingCostUsd = null;
        ApibaraShippingResponse shipping = shippingRateService.getShipping(null, () -> client.getVehicleShipping(slugVin, null));
        if (shipping != null && shipping.data() != null && shipping.data().shipping() != null) {
            shippingCostUsd = shipping.data().shipping().recommendedPriceUsd();
        }

        var result = maxBidCalculator.calculate(MaxBidInput.builder()
            .budgetPln(request.getBudgetPln())
            .estimatedRepairCostPln(request.getEstimatedRepairCostPln())
            .shippingCostUsd(shippingCostUsd)
            .engineCapacityCm3(request.getEngineCapacityCm3())
            .fuelType(request.getFuelType())
            .suv(request.getSuv())
            .build());

        return ResponseEntity.ok(MaxBidResponse.from(result));
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
