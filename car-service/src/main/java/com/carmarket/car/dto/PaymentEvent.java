package com.carmarket.car.dto;

import java.time.Instant;

/**
 * Local copy of payment-service's PaymentEventProducer.PaymentEvent
 * (payment.completed / payment.refunded). Keep the two in sync.
 */
public record PaymentEvent(String paymentId,
                           String userId,
                           String productCode,
                           String referenceId,
                           Integer amount,
                           String currency,
                           Long p24OrderId,
                           Instant occurredAt,
                           Integer durationDays
) {
}
