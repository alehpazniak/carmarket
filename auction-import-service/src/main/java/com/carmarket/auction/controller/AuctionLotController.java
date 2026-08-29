package com.carmarket.auction.controller;

import com.carmarket.auction.dto.CalculationRequest;
import com.carmarket.auction.dto.ImportCalculationResponse;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import com.carmarket.auction.service.AuctionLotService;
import com.carmarket.auction.service.calculator.CalculationInput;
import com.carmarket.auction.service.calculator.ImportCostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionLotController {

    private final AuctionLotService lotService;
    private final ImportCostCalculator costCalculator;
    private final AuctionLotRepository lotRepository;

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

    @GetMapping("/lots/{id}/comparables")
    public ResponseEntity<List<AuctionLot>> getComparables(@PathVariable UUID id) {
        AuctionLot lot = lotRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lot not found"));

        List<AuctionLot> comparables = lotRepository.findSoldComparables(
            lot.getMake(), lot.getModel(), lot.getYear(), lot.getDamageType());

        return ResponseEntity.ok(comparables);
    }
}
