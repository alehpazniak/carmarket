package com.carmarket.payment.dto;

import com.carmarket.payment.entity.Payment;
import com.carmarket.payment.entity.PaymentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /payments/admin/** responses: everything an admin needs, never the P24 token. */
public final class AdminPaymentDtos {

    private AdminPaymentDtos() {
    }

    public record AdminPaymentView(
        UUID id,
        UUID userId,
        String email,
        String productCode,
        String referenceId,
        int amount,
        String currency,
        String description,
        PaymentStatus status,
        Long p24OrderId,
        Integer p24MethodId,
        String failureReason,
        Instant createdAt,
        Instant paidAt,
        Instant refundedAt
    ) {
        public static AdminPaymentView from(Payment p) {
            return new AdminPaymentView(p.getId(), p.getUserId(), p.getEmail(), p.getProductCode(), p.getReferenceId(),
                p.getAmount(), p.getCurrency(), p.getDescription(), p.getStatus(), p.getP24OrderId(), p.getP24MethodId(),
                p.getFailureReason(), p.getCreatedAt(), p.getPaidAt(), p.getRefundedAt());
        }
    }

    /** All amounts in grosz. */
    public record DailyRevenue(LocalDate date, long amount, long count) {
    }

    public record ProductRevenue(String productCode, long amount, long count) {
    }

    public record PaymentStats(
        LocalDate from,
        LocalDate to,
        long revenue,
        List<ProductRevenue> revenueByProduct,
        Map<String, Long> countByStatus,
        long refundedAmount,
        long refundedCount,
        List<DailyRevenue> daily
    ) {
    }
}
