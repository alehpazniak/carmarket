package com.carmarket.car.service;

import com.carmarket.car.audit.AdminAuditPublisher;
import com.carmarket.car.audit.AdminAuditPublisher.Actor;
import com.carmarket.car.dto.AdminListingDtos.AdminListingDetail;
import com.carmarket.car.dto.AdminListingDtos.AdminListingRow;
import com.carmarket.car.dto.AdminListingDtos.DailyCount;
import com.carmarket.car.dto.AdminListingDtos.ListingStats;
import com.carmarket.car.dto.ReportDtos.ReportGroup;
import com.carmarket.car.dto.ReportDtos.ReportView;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingReport;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.entity.ReportStatus;
import com.carmarket.car.exception.CarNotFoundException;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.kafka.ListingModeratedProducer;
import com.carmarket.car.kafka.ListingModeratedProducer.ListingModeratedEvent;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.ListingReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moderation of listings. Lists and counts use native SQL on purpose: the entity's
 * {@code @SQLRestriction} would hide REMOVED listings, which admins need to see.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminListingService {

    private final CarListingRepository listings;
    private final ListingReportRepository reports;
    private final CarListingMapper mapper;
    private final CarEventProducer carEvents;
    private final ListingModeratedProducer moderatedEvents;
    private final AdminAuditPublisher auditPublisher;
    private final S3Service s3Service;
    private final NamedParameterJdbcTemplate jdbc;

    public record ListingFilter(String status, UUID sellerId, String q, LocalDate from, LocalDate to, Boolean promoted) {
    }

    // ─── READ ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminListingRow> search(ListingFilter f, int page, int size, String sort) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (f.status() != null && !f.status().isBlank()) {
            where.append(" AND c.status = :status");
            params.addValue("status", ListingStatus.valueOf(f.status()).name());
        }
        if (f.sellerId() != null) {
            where.append(" AND c.seller_id = :sellerId");
            params.addValue("sellerId", f.sellerId());
        }
        if (f.q() != null && !f.q().isBlank()) {
            where.append(" AND (c.make ILIKE :q OR c.model ILIKE :q OR c.city ILIKE :q OR CAST(c.id AS text) = :exact)");
            params.addValue("q", "%" + f.q().trim().replace("%", "\\%").replace("_", "\\_") + "%");
            params.addValue("exact", f.q().trim());
        }
        if (f.from() != null) {
            where.append(" AND c.created_at >= :from");
            params.addValue("from", Timestamp.valueOf(f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            where.append(" AND c.created_at < :to");
            params.addValue("to", Timestamp.valueOf(f.to().plusDays(1).atStartOfDay()));
        }
        if (Boolean.TRUE.equals(f.promoted())) {
            where.append(" AND (c.promoted_until > now() OR c.highlighted_until > now())");
        } else if (Boolean.FALSE.equals(f.promoted())) {
            where.append(" AND (c.promoted_until IS NULL OR c.promoted_until <= now()) AND (c.highlighted_until IS NULL OR c.highlighted_until <= now())");
        }
        String order = switch (sort == null ? "" : sort) {
            case "createdAt,asc" -> "c.created_at ASC";
            case "price,asc" -> "c.price ASC";
            case "price,desc" -> "c.price DESC";
            case "openReports,desc" -> "open_reports DESC, c.created_at DESC";
            default -> "c.created_at DESC";
        };
        long total = jdbc.queryForObject("SELECT count(*) FROM car_listings c" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<AdminListingRow> rows = jdbc.query("""
                SELECT c.id, c.seller_id, c.make, c.model, c.year, c.price, c.city, c.status, c.primary_image_url,
                       c.created_at, c.promoted_until, c.highlighted_until, c.moderation_reason,
                       (SELECT count(*) FROM listing_reports r WHERE r.car_id = c.id AND r.status = 'OPEN') AS open_reports
                FROM car_listings c""" + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
            params, (rs, i) -> new AdminListingRow(
                rs.getObject("id", UUID.class), rs.getObject("seller_id", UUID.class), rs.getString("make"),
                rs.getString("model"), (Integer) rs.getObject("year"), rs.getBigDecimal("price"), rs.getString("city"),
                rs.getString("status"), rs.getString("primary_image_url"), localDateTime(rs, "created_at"),
                localDateTime(rs, "promoted_until"), localDateTime(rs, "highlighted_until"),
                rs.getString("moderation_reason"), rs.getLong("open_reports")));
        return new PageImpl<>(rows, PageRequest.of(page, size), total);
    }

    @Transactional(readOnly = true)
    public AdminListingDetail get(UUID id) {
        CarListing car = listings.findAnyById(id).orElseThrow(() -> new CarNotFoundException(id.toString()));
        return new AdminListingDetail(mapper.toResponse(car), car.getModeratedBy(), car.getModeratedAt(),
            reports.findByCarIdOrderByCreatedAtDesc(id).stream().map(ReportView::from).toList());
    }

    @Transactional(readOnly = true)
    public Page<ReportGroup> reportQueue(ReportStatus status, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name())
            .addValue("limit", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("SELECT count(DISTINCT car_id) FROM listing_reports WHERE status = :status",
            params, Long.class);
        List<ReportGroup> rows = jdbc.query("""
                SELECT r.car_id, count(*) AS cnt, array_agg(DISTINCT r.reason) AS reasons, max(r.created_at) AS latest,
                       c.make, c.model, c.year, c.status, c.seller_id, c.primary_image_url
                FROM listing_reports r LEFT JOIN car_listings c ON c.id = r.car_id
                WHERE r.status = :status
                GROUP BY r.car_id, c.id
                ORDER BY cnt DESC, latest DESC
                LIMIT :limit OFFSET :offset""",
            params, (rs, i) -> new ReportGroup(rs.getObject("car_id", UUID.class),
                title(rs.getString("make"), rs.getString("model"), (Integer) rs.getObject("year")),
                rs.getString("status"), rs.getObject("seller_id", UUID.class), rs.getString("primary_image_url"),
                rs.getLong("cnt"), strings(rs.getArray("reasons")), instant(rs, "latest")));
        return new PageImpl<>(rows, PageRequest.of(page, size), total);
    }

    @Transactional(readOnly = true)
    public ListingStats stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ListingStatus s : ListingStatus.values()) byStatus.put(s.name(), 0L);
        jdbc.query("SELECT status, count(*) AS cnt FROM car_listings GROUP BY status",
            rs -> { byStatus.put(rs.getString("status"), rs.getLong("cnt")); });

        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(29);
        Map<LocalDate, Long> perDay = new HashMap<>();
        jdbc.query("""
                SELECT CAST(date_trunc('day', created_at) AS date) AS day, count(*) AS cnt
                FROM car_listings WHERE created_at >= :from GROUP BY 1""",
            new MapSqlParameterSource("from", Timestamp.valueOf(firstDay.atStartOfDay())),
            rs -> { perDay.put(rs.getDate("day").toLocalDate(), rs.getLong("cnt")); });
        List<DailyCount> daily = new ArrayList<>();
        for (LocalDate d = firstDay; !d.isAfter(today); d = d.plusDays(1)) {
            daily.add(new DailyCount(d, perDay.getOrDefault(d, 0L)));
        }
        MapSqlParameterSource none = new MapSqlParameterSource();
        long new7 = jdbc.queryForObject("SELECT count(*) FROM car_listings WHERE created_at >= now() - interval '7 days'", none, Long.class);
        long new30 = jdbc.queryForObject("SELECT count(*) FROM car_listings WHERE created_at >= now() - interval '30 days'", none, Long.class);
        long openReports = jdbc.queryForObject("SELECT count(*) FROM listing_reports WHERE status = 'OPEN'", none, Long.class);
        long reported = jdbc.queryForObject("SELECT count(DISTINCT car_id) FROM listing_reports WHERE status = 'OPEN'", none, Long.class);
        long promotions = jdbc.queryForObject("""
            SELECT count(*) FROM car_listings
            WHERE status = 'ACTIVE' AND (promoted_until > now() OR highlighted_until > now())""", none, Long.class);
        return new ListingStats(byStatus, new7, new30, daily, openReports, reported, promotions);
    }

    // ─── WRITE ────────────────────────────────────────────────────────────

    /** Hides the listing (any status but REMOVED), resolves its open reports and emails the seller. */
    @Transactional
    public AdminListingDetail block(UUID id, String reason, Actor admin) {
        CarListing car = listings.findByIdForUpdate(id).orElseThrow(() -> new CarNotFoundException(id.toString()));
        if (car.getStatus() != ListingStatus.BLOCKED) {
            String previous = car.getStatus().name();
            Instant now = Instant.now();
            car.setStatus(ListingStatus.BLOCKED);
            car.setModerationReason(reason);
            car.setModeratedBy(UUID.fromString(admin.id()));
            car.setModeratedAt(now);
            carEvents.publishUpdated(listings.save(car));

            List<ListingReport> open = reports.findByCarIdAndStatus(id, ReportStatus.OPEN);
            for (ListingReport r : open) {
                r.setStatus(ReportStatus.RESOLVED);
                r.setResolvedBy(car.getModeratedBy());
                r.setResolvedAt(now);
                r.setResolutionNote(reason);
            }
            reports.saveAll(open);

            moderatedEvents.publish(new ListingModeratedEvent(id.toString(), car.getSellerId().toString(), "BLOCKED",
                reason, title(car), now));
            auditPublisher.publish(admin, "LISTING_BLOCKED", "LISTING", id.toString(), reason, Map.of(
                "previousStatus", previous, "sellerId", car.getSellerId().toString(), "resolvedReports", open.size()));
            log.info("Admin {} blocked listing {}", admin.id(), id);
        }
        return get(id);
    }

    @Transactional
    public AdminListingDetail unblock(UUID id, String reason, Actor admin) {
        CarListing car = listings.findByIdForUpdate(id).orElseThrow(() -> new CarNotFoundException(id.toString()));
        if (car.getStatus() != ListingStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a blocked listing can be unblocked");
        }
        Instant now = Instant.now();
        car.setStatus(ListingStatus.ACTIVE);
        car.setModerationReason(null);
        car.setModeratedBy(UUID.fromString(admin.id()));
        car.setModeratedAt(now);
        carEvents.publishUpdated(listings.save(car));
        moderatedEvents.publish(new ListingModeratedEvent(id.toString(), car.getSellerId().toString(), "UNBLOCKED",
            reason, title(car), now));
        auditPublisher.publish(admin, "LISTING_UNBLOCKED", "LISTING", id.toString(), reason,
            Map.of("sellerId", car.getSellerId().toString()));
        log.info("Admin {} unblocked listing {}", admin.id(), id);
        return get(id);
    }

    /** Removes one photo (and its S3 object, after commit). */
    @Transactional
    public AdminListingDetail removeImage(UUID id, String url, String reason, Actor admin) {
        CarListing car = listings.findByIdForUpdate(id).orElseThrow(() -> new CarNotFoundException(id.toString()));
        if (url == null || !car.getImageUrls().remove(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image does not belong to this listing");
        }
        if (url.equals(car.getPrimaryImageUrl())) {
            car.setPrimaryImageUrl(car.getImageUrls().stream().findFirst().orElse(null));
        }
        carEvents.publishUpdated(listings.save(car));
        afterCommit(() -> s3Service.deleteImage(url));
        auditPublisher.publish(admin, "LISTING_IMAGE_REMOVED", "LISTING", id.toString(), reason, Map.of("imageUrl", url));
        return get(id);
    }

    @Transactional
    public int dismissReports(UUID carId, String note, Actor admin) {
        List<ListingReport> open = reports.findByCarIdAndStatus(carId, ReportStatus.OPEN);
        if (open.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No open reports for this listing");
        }
        Instant now = Instant.now();
        for (ListingReport r : open) {
            r.setStatus(ReportStatus.DISMISSED);
            r.setResolvedBy(UUID.fromString(admin.id()));
            r.setResolvedAt(now);
            r.setResolutionNote(note);
        }
        reports.saveAll(open);
        auditPublisher.publish(admin, "REPORTS_DISMISSED", "LISTING", carId.toString(), note, Map.of("dismissed", open.size()));
        return open.size();
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private static String title(CarListing car) {
        return title(car.getMake(), car.getModel(), car.getYear());
    }

    private static String title(String make, String model, Integer year) {
        if (make == null) return null;
        return make + " " + (model != null ? model : "") + (year != null ? " (" + year + ")" : "");
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((Object[]) array.getArray()).map(String::valueOf).sorted().toList();
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
