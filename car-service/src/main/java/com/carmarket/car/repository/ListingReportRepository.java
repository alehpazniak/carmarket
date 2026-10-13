package com.carmarket.car.repository;

import com.carmarket.car.entity.ListingReport;
import com.carmarket.car.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListingReportRepository extends JpaRepository<ListingReport, UUID> {

    boolean existsByCarIdAndReporterIdAndStatus(UUID carId, UUID reporterId, ReportStatus status);

    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);

    List<ListingReport> findByCarIdAndStatus(UUID carId, ReportStatus status);

    List<ListingReport> findByCarIdOrderByCreatedAtDesc(UUID carId);

    long countByStatus(ReportStatus status);
}
