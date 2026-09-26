package com.carmarket.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private UUID id;

    /** Our transaction id as sent to P24 (sessionId); equals id.toString(). */
    @Column(name = "session_id", nullable = false, unique = true, length = 100)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    /** What is being paid for, e.g. the car listing id for LISTING_PROMOTION. */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    /** In grosz (1.23 PLN = 123). */
    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(nullable = false, length = 50)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "p24_token", length = 100)
    private String p24Token;

    @Column(name = "p24_order_id")
    private Long p24OrderId;

    @Column(name = "p24_method_id")
    private Integer p24MethodId;

    @Column(name = "p24_statement")
    private String p24Statement;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
