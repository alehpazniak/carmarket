package com.carmarket.car.controller;

import com.carmarket.car.dto.CarListingRequest;
import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.service.CarListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Car listing REST API.
 * All endpoints receive X-User-Id from the gateway JWT filter.
 * <p>
 * GET    /cars               → public list of active listings
 * GET    /cars/{id}          → public single listing
 * GET    /cars/my            → authenticated seller's own listings
 * POST   /cars               → create listing (authenticated)
 * PUT    /cars/{id}          → update own listing (authenticated)
 * PATCH  /cars/{id}/sold     → mark as sold  (authenticated, owner only)
 * DELETE /cars/{id}          → soft-delete   (authenticated, owner only)
 */
@RestController
@RequestMapping("/cars")
@RequiredArgsConstructor
public class CarListingController {

    private final CarListingService service;

    @GetMapping
    public ResponseEntity<Page<CarListingResponse>> listAll(
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(service.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarListingResponse> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<CarListingResponse>> myListings(@RequestHeader("X-User-Id") String userId,
                                                               @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(service.getMyListings(UUID.fromString(userId), pageable));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<CarListingResponse> create(@Valid @RequestBody CarListingRequest request,
                                                     @RequestHeader("X-User-Id") String userId) {
        CarListingResponse response = service.create(request, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CarListingResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody CarListingRequest request,
                                                     @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(service.update(id, request, UUID.fromString(userId)));
    }

    @PatchMapping("/{id}/sold")
    public ResponseEntity<CarListingResponse> markAsSold(@PathVariable UUID id,
                                                         @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(service.markAsSold(id, UUID.fromString(userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestHeader("X-User-Id") String userId) {
        service.delete(id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<List<String>> uploadImages(@PathVariable UUID id, @RequestHeader("X-User-Id") String userId,
                                                     @RequestParam("files") List<MultipartFile> files) {
        List<String> urls = service.uploadImages(id, UUID.fromString(userId), files);
        return ResponseEntity.ok(urls);
    }

    @PatchMapping("/{id}/images/primary")
    public ResponseEntity<CarListingResponse> setPrimaryImage(@PathVariable UUID id,
                                                              @RequestHeader("X-User-Id") String userId,
                                                              @RequestParam String url) {
        return ResponseEntity.ok(service.setPrimaryImage(id, UUID.fromString(userId), url));
    }

    @DeleteMapping("/{id}/images")
    public ResponseEntity<CarListingResponse> removeImage(@PathVariable UUID id,
                                                          @RequestHeader("X-User-Id") String userId,
                                                          @RequestParam String url) {
        return ResponseEntity.ok(service.removeImage(id, UUID.fromString(userId), url));
    }
}
