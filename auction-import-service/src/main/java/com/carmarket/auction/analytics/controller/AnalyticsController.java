package com.carmarket.auction.analytics.controller;

import com.carmarket.auction.analytics.entity.VehicleStats;
import com.carmarket.auction.analytics.repository.VehicleStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final VehicleStatsRepository statsRepository;

    @GetMapping("/stats")
    public ResponseEntity<VehicleStats> getStats(
        @RequestParam String make,
        @RequestParam String model,
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) String damageType) {

        return statsRepository.findByMakeAndModelAndYearAndDamageType(make, model, year, damageType)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/{make}/{model}")
    public ResponseEntity<List<VehicleStats>> getModelHistory(
        @PathVariable String make,
        @PathVariable String model) {

        return ResponseEntity.ok(statsRepository.findByMakeAndModelOrderByYearDesc(make, model));
    }

    @GetMapping("/top-profit/{make}")
    public ResponseEntity<List<VehicleStats>> getTopProfitable(
        @PathVariable String make) {

        return ResponseEntity.ok(statsRepository.findMostProfitableByMake(make));
    }
}
