package com.carmarket.car.service;

import com.carmarket.car.dto.ReportDtos.CreateReportRequest;
import com.carmarket.car.dto.ReportDtos.ReportView;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingReport;
import com.carmarket.car.entity.ReportStatus;
import com.carmarket.car.exception.CarNotFoundException;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.ListingReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingReportService {

    static final int DAILY_LIMIT = 10;

    private final CarListingRepository listings;
    private final ListingReportRepository reports;

    @Transactional
    public ReportView create(UUID carId, UUID reporterId, CreateReportRequest request) {
        CarListing car = listings.findById(carId)
            .filter(c -> c.getStatus().isPubliclyVisible())
            .orElseThrow(() -> new CarNotFoundException(carId.toString()));
        if (car.getSellerId().equals(reporterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't report your own listing");
        }
        if (reports.existsByCarIdAndReporterIdAndStatus(carId, reporterId, ReportStatus.OPEN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reported this listing");
        }
        if (reports.countByReporterIdAndCreatedAtAfter(reporterId, Instant.now().minus(Duration.ofDays(1))) >= DAILY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Report limit reached, try again tomorrow");
        }
        String comment = request.comment() != null && !request.comment().isBlank() ? request.comment().trim() : null;
        try {
            ListingReport saved = reports.saveAndFlush(ListingReport.builder()
                .carId(carId).reporterId(reporterId).reason(request.reason()).comment(comment).build());
            log.info("Listing {} reported by {} ({})", carId, reporterId, request.reason());
            return ReportView.from(saved);
        } catch (DataIntegrityViolationException e) {
            // Two clicks at once: the partial unique index keeps one open report
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reported this listing");
        }
    }
}
