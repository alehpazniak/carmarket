package com.carmarket.search.controller;

import com.carmarket.search.document.CarDocument;
import com.carmarket.search.dto.SearchRequest;
import com.carmarket.search.service.CarSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Search REST API — all endpoints are PUBLIC (no auth required).
 * Gateway routes /api/search/** → search-service.
 * <p>
 * GET /search?query=toyota&yearFrom=2015&priceFrom=5000&priceTo=20000
 * &city=Warsaw&fuelType=DIESEL&page=0&size=20&sort=price,asc
 * <p>
 * GET /search/{id}   — get single car document from Elasticsearch
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final CarSearchService searchService;

    /**
     * Dynamic full-text + filter search.
     * All params are optional — combine freely.
     */
    @GetMapping
    public ResponseEntity<List<CarDocument>> search(@RequestParam(required = false) String query,
                                                    @RequestParam(required = false) String make,
                                                    @RequestParam(required = false) String model,
                                                    @RequestParam(required = false) Integer yearFrom,
                                                    @RequestParam(required = false) Integer yearTo,
                                                    @RequestParam(required = false) BigDecimal priceFrom,
                                                    @RequestParam(required = false) BigDecimal priceTo,
                                                    @RequestParam(required = false) Integer mileageMax,
                                                    @RequestParam(required = false) String fuelType,
                                                    @RequestParam(required = false) String transmission,
                                                    @RequestParam(required = false) String city,
                                                    @RequestParam(required = false) String country,
                                                    @PageableDefault(size = 20) Pageable pageable) {

        SearchRequest req = SearchRequest.builder()
            .query(query)
            .make(make)
            .model(model)
            .yearFrom(yearFrom)
            .yearTo(yearTo)
            .priceFrom(priceFrom)
            .priceTo(priceTo)
            .mileageMax(mileageMax)
            .fuelType(fuelType)
            .transmission(transmission)
            .city(city)
            .country(country)
            .build();

        return ResponseEntity.ok(searchService.search(req, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarDocument> getOne(@PathVariable String id) {
        return searchService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
