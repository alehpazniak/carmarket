package com.carmarket.car.dto;

import com.carmarket.car.dto.ReportDtos.ReportView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /cars/admin/** responses. */
public final class AdminListingDtos {

    private AdminListingDtos() {
    }

    public record AdminListingRow(UUID id, UUID sellerId, String make, String model, Integer year, BigDecimal price,
                                  String city, String status, String primaryImageUrl, LocalDateTime createdAt,
                                  LocalDateTime promotedUntil, LocalDateTime highlightedUntil, String moderationReason,
                                  long openReports) {
    }

    public record AdminListingDetail(CarListingResponse listing, UUID moderatedBy, Instant moderatedAt,
                                     List<ReportView> reports) {
    }

    public record DailyCount(LocalDate date, long count) {
    }

    public record ListingStats(Map<String, Long> byStatus, long newLast7Days, long newLast30Days, List<DailyCount> daily,
                               long openReports, long listingsWithOpenReports, long activePromotions) {
    }
}
