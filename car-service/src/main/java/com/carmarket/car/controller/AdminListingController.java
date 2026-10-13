package com.carmarket.car.controller;

import com.carmarket.car.audit.AdminAuditPublisher.Actor;
import com.carmarket.car.dto.AdminListingDtos.AdminListingDetail;
import com.carmarket.car.dto.AdminListingDtos.AdminListingRow;
import com.carmarket.car.dto.AdminListingDtos.ListingStats;
import com.carmarket.car.dto.ReportDtos.NoteRequest;
import com.carmarket.car.dto.ReportDtos.ReasonRequest;
import com.carmarket.car.dto.ReportDtos.ReportGroup;
import com.carmarket.car.entity.ReportStatus;
import com.carmarket.car.service.AdminListingService;
import com.carmarket.car.service.AdminListingService.ListingFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Gateway: /api/cars/admin/** — ADMIN only (SecurityConfig), never public (JwtAuthenticationFilter). */
@RestController
@RequestMapping("/cars/admin")
@RequiredArgsConstructor
public class AdminListingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminListingService service;

    @GetMapping("/listings")
    public Page<AdminListingRow> listings(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) UUID sellerId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) Boolean promoted,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.search(new ListingFilter(status, sellerId, q, from, to, promoted),
            Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    @GetMapping("/listings/{id}")
    public AdminListingDetail listing(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/listings/{id}/block")
    public AdminListingDetail block(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                                    @AuthenticationPrincipal String adminId,
                                    @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        return service.block(id, request.reason(), new Actor(adminId, adminEmail));
    }

    @PostMapping("/listings/{id}/unblock")
    public AdminListingDetail unblock(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                                      @AuthenticationPrincipal String adminId,
                                      @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        return service.unblock(id, request.reason(), new Actor(adminId, adminEmail));
    }

    /** The reason travels in the body (not the URL, which ends up in access logs). */
    @DeleteMapping("/listings/{id}/images")
    public AdminListingDetail removeImage(@PathVariable UUID id, @RequestParam String url,
                                          @Valid @RequestBody ReasonRequest request,
                                          @AuthenticationPrincipal String adminId,
                                          @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        return service.removeImage(id, url, request.reason(), new Actor(adminId, adminEmail));
    }

    @GetMapping("/reports")
    public Page<ReportGroup> reports(@RequestParam(defaultValue = "OPEN") ReportStatus status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.reportQueue(status, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @PostMapping("/reports/listing/{carId}/dismiss")
    public Map<String, Integer> dismiss(@PathVariable UUID carId, @Valid @RequestBody NoteRequest request,
                                        @AuthenticationPrincipal String adminId,
                                        @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        return Map.of("dismissed", service.dismissReports(carId, request.note(), new Actor(adminId, adminEmail)));
    }

    @GetMapping("/stats")
    public ListingStats stats() {
        return service.stats();
    }
}
