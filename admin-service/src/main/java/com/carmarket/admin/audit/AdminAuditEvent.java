package com.carmarket.admin.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * admin.audit payload. Every service that has admin writes has its own copy of the producer side
 * (AdminAuditPublisher.AdminAuditEvent) — keep the field names in sync.
 */
public record AdminAuditEvent(
    UUID eventId,
    Instant occurredAt,
    String actorId,
    String actorEmail,
    String service,
    String action,
    String targetType,
    String targetId,
    String reason,
    Map<String, Object> details
) {
}
