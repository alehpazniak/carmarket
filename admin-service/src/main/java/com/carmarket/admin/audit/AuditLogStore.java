package com.carmarket.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/** Idempotent insert: the event id is the primary key, so a redelivered event changes nothing. */
@Component
@RequiredArgsConstructor
public class AuditLogStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** @return whether a new row was written */
    public boolean save(AdminAuditEvent event) {
        String details;
        try {
            details = objectMapper.writeValueAsString(event.details() != null ? event.details() : Map.of());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("details is not serializable", e);
        }
        int rows = jdbc.update("""
                INSERT INTO admin_audit_log (id, occurred_at, actor_id, actor_email, service, action,
                                             target_type, target_id, reason, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO NOTHING
                """,
            event.eventId(),
            Timestamp.from(event.occurredAt() != null ? event.occurredAt() : Instant.now()),
            event.actorId(), truncate(event.actorEmail(), 320), event.service(), event.action(),
            event.targetType(), truncate(event.targetId(), 128), event.reason(), details);
        return rows > 0;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
