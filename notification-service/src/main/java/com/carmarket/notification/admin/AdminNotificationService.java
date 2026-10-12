package com.carmarket.notification.admin;

import com.carmarket.notification.audit.AdminAuditPublisher;
import com.carmarket.notification.entity.NotificationStatus;
import com.carmarket.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read the email outbox and requeue FAILED emails; the payload (links, tokens) is never exposed. */
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditPublisher auditPublisher;

    public record NotificationView(UUID id, String type, String status, UUID recipientId, String recipientEmail,
                                   Instant createdAt, Instant sendAt, Instant sentAt, int attempts, String lastError) {
    }

    public record NotificationStats(Map<String, Long> byStatus, long failedLast24h, long sentLast7Days,
                                    Map<String, Long> failedByType, long pending) {
    }

    @Transactional(readOnly = true)
    public Page<NotificationView> list(NotificationStatus status, NotificationType type, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (status != null) {
            where.append(" AND n.status = :status");
            params.addValue("status", status.name());
        }
        if (type != null) {
            where.append(" AND n.type = :type");
            params.addValue("type", type.name());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM notifications n" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<NotificationView> rows = jdbc.query("""
                SELECT n.id, n.type, n.status, n.recipient_id, r.email, n.created_at, n.send_at, n.sent_at, n.attempts, n.last_error
                FROM notifications n LEFT JOIN recipients r ON r.user_id = n.recipient_id""" + where
                + " ORDER BY n.created_at DESC LIMIT :limit OFFSET :offset",
            params, (rs, i) -> new NotificationView(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getString("status"), rs.getObject("recipient_id", UUID.class), rs.getString("email"),
                instant(rs, "created_at"), instant(rs, "send_at"), instant(rs, "sent_at"), rs.getInt("attempts"),
                rs.getString("last_error")));
        return new PageImpl<>(rows, PageRequest.of(page, size), total);
    }

    /** FAILED → PENDING with attempts reset; the dispatcher picks it up on its next round. */
    @Transactional
    public void retry(UUID id, AdminAuditPublisher.Actor admin) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(Instant.now()));
        int updated = jdbc.update("""
            UPDATE notifications SET status = 'PENDING', attempts = 0, send_at = :now, last_error = NULL
            WHERE id = :id AND status = 'FAILED'""", params);
        if (updated == 0) {
            Long exists = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE id = :id", params, Long.class);
            throw exists != null && exists > 0
                ? new ResponseStatusException(HttpStatus.CONFLICT, "Only a failed notification can be retried")
                : new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        String type = jdbc.queryForObject("SELECT type FROM notifications WHERE id = :id", params, String.class);
        auditPublisher.publish(admin, "NOTIFICATION_RETRIED", "NOTIFICATION", id.toString(), null, Map.of("type", type));
    }

    @Transactional(readOnly = true)
    public NotificationStats stats() {
        MapSqlParameterSource none = new MapSqlParameterSource();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (NotificationStatus s : NotificationStatus.values()) byStatus.put(s.name(), 0L);
        jdbc.query("SELECT status, count(*) AS cnt FROM notifications GROUP BY status", none,
            rs -> { byStatus.put(rs.getString("status"), rs.getLong("cnt")); });
        Map<String, Long> failedByType = new LinkedHashMap<>();
        jdbc.query("SELECT type, count(*) AS cnt FROM notifications WHERE status = 'FAILED' GROUP BY type ORDER BY 2 DESC", none,
            rs -> { failedByType.put(rs.getString("type"), rs.getLong("cnt")); });
        long failed24h = jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE status = 'FAILED' AND created_at >= now() - interval '24 hours'", none, Long.class);
        long sent7 = jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE sent_at >= now() - interval '7 days'", none, Long.class);
        return new NotificationStats(byStatus, failed24h, sent7, failedByType, byStatus.get("PENDING"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }
}
