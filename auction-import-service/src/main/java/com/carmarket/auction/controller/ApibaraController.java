package com.carmarket.auction.controller;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraQuotaExceededException;
import com.carmarket.auction.client.ApibaraUnavailableException;
import com.carmarket.auction.client.ApibaraResponse;
import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.client.ApibaraVehicleDetailResponse;
import com.carmarket.auction.dto.MaxBidRequest;
import com.carmarket.auction.dto.MaxBidResponse;
import com.carmarket.auction.service.LiveVehicleLookup;
import com.carmarket.auction.service.UsaShippingRateService;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.service.shipping.W8QuoteRequest;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import jakarta.validation.Valid;
import com.carmarket.auction.service.calculator.MaxBidCalculator;
import com.carmarket.auction.service.calculator.CustomsDutyResolver;
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
import java.util.Optional;
import java.util.function.Supplier;

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
    private final W8ShippingQuoteService w8QuoteService;
    private final UsaShippingRateService shippingRateService;
    private final LiveVehicleLookup liveLookup;

    @GetMapping("/vehicles/filters")
    public ResponseEntity<JsonNode> getFilters() {
        return okOrBadGateway(client.getFiltersMetadata());
    }

    /**
     * A pasted Copart/IAAI link, lot number or VIN → the vehicle (same shape as GET /vehicles/{slugVin}). Both are
     * cached and stored when they pass the sync's filters ({@link LiveVehicleLookup}). 404 when Apibara doesn't know
     * the car, 429 when Apibara's quota is used up, 502 when it failed, 503 without an API key.
     */
    @GetMapping("/vehicles/url-to-details")
    public ResponseEntity<ApibaraVehicleDetailResponse> resolveFromUrl(@RequestParam String url) {
        return live(() -> liveLookup.resolve(url));
    }

    @GetMapping("/vehicles")
    public ResponseEntity<ApibaraResponse> searchVehicles(@RequestParam MultiValueMap<String, String> query) {
        return okOrBadGateway(client.searchVehicles(query));
    }

    @GetMapping("/vehicles/{slugVin}")
    public ResponseEntity<ApibaraVehicleDetailResponse> getVehicle(@PathVariable String slugVin) {
        return live(() -> liveLookup.vehicle(slugVin));
    }

    private ResponseEntity<ApibaraVehicleDetailResponse> live(Supplier<Optional<ApibaraVehicleDetailResponse>> lookup) {
        if (!client.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        try {
            return lookup.get().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (ApibaraQuotaExceededException e) {
            log.warn("Apibara live lookup: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        } catch (ApibaraUnavailableException e) {
            log.error("Apibara live lookup failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
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
        @Valid @RequestBody MaxBidRequest request) {

        // W8's table first: it prices the yard for free, an Apibara shipping lookup spends the monthly quota
        W8ShippingQuote w8 = w8QuoteService.quote(new W8QuoteRequest(W8Auction.from(request.getAuction()).orElse(null),
            request.getAuctionLocation(), request.getSuv(), request.getPolandZone(), request.getLengthClass())).orElse(null);

        BigDecimal shippingCostUsd = null;
        if (w8 == null) {
            ApibaraShippingResponse shipping = shippingRateService.getShipping(null, () -> client.getVehicleShipping(slugVin, null));
            if (shipping != null && shipping.data() != null && shipping.data().shipping() != null) {
                shippingCostUsd = shipping.data().shipping().recommendedPriceUsd();
            }
        }

        var result = maxBidCalculator.calculate(MaxBidInput.builder()
            .budgetPln(request.getBudgetPln())
            .estimatedRepairCostPln(request.getEstimatedRepairCostPln())
            .shippingCostUsd(shippingCostUsd)
            .w8Quote(w8)
            .engineCapacityCm3(request.getEngineCapacityCm3())
            .fuelType(request.getFuelType())
            .suv(request.getSuv())
            .vin(vinFor(slugVin, request.getVin()))
            .build());

        return ResponseEntity.ok(MaxBidResponse.from(result));
    }

    /**
     * The identifier is a VIN, a lot number or Apibara's slug ("...-{vin}"), so the VIN is taken from it when it
     * ends with one, else from the request body (the frontend sends the vehicle's VIN); null → standard duty.
     */
    static String vinFor(String slugVin, String requestVin) {
        String last = slugVin.substring(slugVin.lastIndexOf('-') + 1);
        if (CustomsDutyResolver.isVin(last)) return last;
        return CustomsDutyResolver.isVin(requestVin) ? requestVin : null;
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
