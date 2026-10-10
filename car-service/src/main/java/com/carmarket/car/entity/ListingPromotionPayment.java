package com.carmarket.car.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A payment.completed event that has been processed, see {@code ListingPromotionService}. */
@Entity
@Table(name = "listing_promotion_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingPromotionPayment {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    /** 0 when the payment was not applied (listing missing, not ACTIVE or not the buyer's). */
    @Column(name = "days_applied", nullable = false)
    private int daysApplied;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
