package com.carmarket.admin.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Read model of admin_audit_log; rows are only ever inserted by {@link AuditLogStore}. */
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor
public class AuditLogEntry {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(nullable = false)
    private String service;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;
}
