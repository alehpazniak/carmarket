package com.carmarket.car.dto;

import com.carmarket.car.entity.ListingReport;
import com.carmarket.car.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record CreateReportRequest(@NotNull ReportReason reason, @Size(max = 1000) String comment) {
    }

    /** Moderator decisions need a reason / note of at least 5 characters (audit log, seller email). */
    public record ReasonRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
    }

    public record NoteRequest(@NotBlank @Size(min = 5, max = 500) String note) {
    }

    public record ReportView(UUID id, UUID carId, UUID reporterId, String reason, String comment, String status,
                             Instant createdAt, UUID resolvedBy, Instant resolvedAt, String resolutionNote) {
        public static ReportView from(ListingReport r) {
            return new ReportView(r.getId(), r.getCarId(), r.getReporterId(), r.getReason().name(), r.getComment(),
                r.getStatus().name(), r.getCreatedAt(), r.getResolvedBy(), r.getResolvedAt(), r.getResolutionNote());
        }
    }

    /** One row of the moderation queue: all reports of one listing in the given status. */
    public record ReportGroup(UUID carId, String title, String listingStatus, UUID sellerId, String primaryImageUrl,
                              long count, List<String> reasons, Instant latestAt) {
    }
}
