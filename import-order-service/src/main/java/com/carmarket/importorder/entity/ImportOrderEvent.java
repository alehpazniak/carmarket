package com.carmarket.importorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One step of an order's status timeline. */
@Entity
@Table(name = "import_order_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportOrderEvent {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportOrderStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Shown on the client page. */
    @Column(name = "client_note")
    private String clientNote;

    /** Dealer only — never in the client view. */
    @Column(name = "internal_note")
    private String internalNote;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
