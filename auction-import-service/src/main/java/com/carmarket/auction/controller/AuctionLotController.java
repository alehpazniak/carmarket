package com.carmarket.auction.controller;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraShippingResponse;
import com.carmarket.auction.dto.CalculationRequest;
import com.carmarket.auction.dto.ImportCalculationResponse;
import com.carmarket.auction.dto.MaxBidRequest;
import com.carmarket.auction.dto.MaxBidResponse;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import com.carmarket.auction.service.AuctionLotService;
import com.carmarket.auction.service.UsaShippingRateService;
import com.carmarket.auction.service.calculator.CalculationInput;
import com.carmarket.auction.service.calculator.ImportCostCalculator;
import com.carmarket.auction.service.calculator.MaxBidCalculator;
import com.carmarket.auction.service.calculator.MaxBidInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionLotController {

    private final AuctionLotService lotService;
    private final ImportCostCalculator costCalculator;
    private final MaxBidCalculator maxBidCalculator;
    private final AuctionLotRepository lotRepository;
    private final ApibaraClient apibaraClient;
    private final UsaShippingRateService shippingRateService;

    @GetMapping("/lots")
    public ResponseEntity<Page<AuctionLot>> searchLots(
        @RequestParam(required = false) String make,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) Integer yearFrom,
        @RequestParam(required = false) Integer yearTo,
        @RequestParam(required = false) String damageType,
        @PageableDefault(size = 20) Pageable pageable) {

        // Build the LIKE pattern in Java (pre-lowercased) rather than via JPQL CONCAT: binding a null
        // parameter through CONCAT('%', :model, '%') makes the Postgres JDBC driver mis-infer the
        // parameter type as bytea, causing "function lower(bytea) does not exist".
        String modelPattern = (model == null || model.isBlank()) ? null : "%" + model.toLowerCase() + "%";

        Page<AuctionLot> lots = lotRepository.searchLiveLots(
            make, modelPattern, yearFrom, yearTo, null, null, damageType, pageable);
        return ResponseEntity.ok(lots);
    }

    @GetMapping("/lots/{id}")
    public ResponseEntity<AuctionLot> getLot(@PathVariable UUID id) {
        return lotRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new RuntimeException("Lot not found"));
    }

    @GetMapping("/lots/vin/{vin}")
    public ResponseEntity<List<AuctionLot>> getByVin(@PathVariable String vin) {
        return ResponseEntity.ok(lotRepository.findByVin(vin));
    }

    @PostMapping("/lots/{id}/calculate")
    public ResponseEntity<ImportCalculationResponse> calculateImport(
        @PathVariable UUID id,
        @RequestBody CalculationRequest request) {

        AuctionLot lot = lotRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lot not found"));

        var calculation = costCalculator.calculate(lot, CalculationInput.builder()
            .estimatedRepairCostPln(request.getEstimatedRepairCostPln())
            .targetSalePricePln(request.getTargetSalePricePln())
            .destinationCountry(request.getDestinationCountry())
            .build());

        return ResponseEntity.ok(ImportCalculationResponse.from(calculation));
    }

    @PostMapping("/lots/{id}/max-bid")
    public ResponseEntity<MaxBidResponse> calculateMaxBid(
        @PathVariable UUID id,
        @RequestBody MaxBidRequest request) {

        AuctionLot lot = lotRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lot not found"));

        BigDecimal shippingCostUsd = resolveShippingCostUsd(lot);

        var result = maxBidCalculator.calculate(MaxBidInput.builder()
            .budgetPln(request.getBudgetPln())
            .estimatedRepairCostPln(request.getEstimatedRepairCostPln())
            .shippingCostUsd(shippingCostUsd)
            .engineCapacityCm3(request.getEngineCapacityCm3() != null ? request.getEngineCapacityCm3() : lot.getEngineCapacity())
            .fuelType(request.getFuelType() != null ? request.getFuelType() : lot.getFuelType())
            .suv(request.getSuv())
            .build());

        return ResponseEntity.ok(MaxBidResponse.from(result));
    }

    /** Real Apibara shipping quote for the lot; falls back to the configured flat estimate if unavailable. */
    private BigDecimal resolveShippingCostUsd(AuctionLot lot) {
        try {
            ApibaraShippingResponse shipping = shippingRateService.getShipping(
                lot.getAuctionLocation(),
                () -> apibaraClient.getAuctionToPortShipping(lot.getVin(), lot.getLotNumber(), null));
            if (shipping != null && shipping.data() != null && shipping.data().shipping() != null
                && shipping.data().shipping().recommendedPriceUsd() != null) {
                return shipping.data().shipping().recommendedPriceUsd();
            }
        } catch (Exception e) {
            log.warn("Apibara shipping lookup failed for lot {}: {}", lot.getLotNumber(), e.getMessage());
        }
        return null; // MaxBidCalculator falls back to the configured flat ocean-freight estimate
    }

    @GetMapping("/lots/{id}/comparables")
    public ResponseEntity<List<AuctionLot>> getComparables(@PathVariable UUID id) {
        AuctionLot lot = lotRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lot not found"));

        List<AuctionLot> comparables = lotRepository.findSoldComparables(
            lot.getMake(), lot.getModel(), lot.getYear(), lot.getDamageType());

        return ResponseEntity.ok(comparables);
    }
}
