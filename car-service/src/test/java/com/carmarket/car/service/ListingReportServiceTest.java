package com.carmarket.car.service;

import com.carmarket.car.dto.ReportDtos.CreateReportRequest;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingReport;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.entity.ReportReason;
import com.carmarket.car.entity.ReportStatus;
import com.carmarket.car.exception.CarNotFoundException;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.ListingReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingReportServiceTest {

    private final CarListingRepository listings = mock(CarListingRepository.class);
    private final ListingReportRepository reports = mock(ListingReportRepository.class);
    private final ListingReportService service = new ListingReportService(listings, reports);
    private final UUID reporter = UUID.randomUUID();
    private CarListing car;

    @BeforeEach
    void setUp() {
        car = new CarListing();
        car.setId(UUID.randomUUID());
        car.setSellerId(UUID.randomUUID());
        car.setStatus(ListingStatus.ACTIVE);
        when(listings.findById(car.getId())).thenReturn(Optional.of(car));
        when(reports.saveAndFlush(any(ListingReport.class))).thenAnswer(inv -> {
            ListingReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(java.time.Instant.now());
            return r;
        });
    }

    private ResponseStatusException reject(UUID who) {
        return (ResponseStatusException) org.assertj.core.api.Assertions.catchThrowable(
            () -> service.create(car.getId(), who, new CreateReportRequest(ReportReason.SCAM, "fake price")));
    }

    @Test
    void aReportIsStoredOpen() {
        var view = service.create(car.getId(), reporter, new CreateReportRequest(ReportReason.WRONG_DATA, "  wrong year "));
        assertThat(view.status()).isEqualTo("OPEN");
        assertThat(view.comment()).isEqualTo("wrong year");
    }

    @Test
    void theOwnerCantReportTheirOwnListing() {
        assertThat(reject(car.getSellerId()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void oneOpenReportPerUserAndListing() {
        when(reports.existsByCarIdAndReporterIdAndStatus(car.getId(), reporter, ReportStatus.OPEN)).thenReturn(true);
        assertThat(reject(reporter).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    void atMostTenReportsPerDay() {
        when(reports.countByReporterIdAndCreatedAtAfter(eq(reporter), any())).thenReturn(10L);
        assertThat(reject(reporter).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        when(reports.countByReporterIdAndCreatedAtAfter(eq(reporter), any())).thenReturn(9L);
        assertThat(service.create(car.getId(), reporter, new CreateReportRequest(ReportReason.OTHER, null))).isNotNull();
    }

    @Test
    void hiddenListingsCantBeReported() {
        car.setStatus(ListingStatus.BLOCKED);
        assertThatThrownBy(() -> service.create(car.getId(), reporter, new CreateReportRequest(ReportReason.SCAM, null)))
            .isInstanceOf(CarNotFoundException.class);
    }
}
