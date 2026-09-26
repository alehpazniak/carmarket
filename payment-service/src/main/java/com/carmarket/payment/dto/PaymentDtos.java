package com.carmarket.payment.dto;

import com.carmarket.payment.entity.Payment;
import com.carmarket.payment.entity.PaymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * Client asks to pay for a product. The amount is looked up server-side from the product catalogue;
     * email is only needed when the JWT carries none.
     */
    public record CreatePaymentRequest(
        @NotBlank @Size(max = 50) String productCode,
        @Size(max = 100) String referenceId,
        @Email @Size(max = 50) String email
    ) {
    }

    /** redirectUrl is the Przelewy24 payment page the browser must be sent to. */
    public record CreatePaymentResponse(UUID paymentId, PaymentStatus status, String redirectUrl) {
    }

    public record PaymentResponse(
        UUID id,
        String productCode,
        String referenceId,
        int amount,
        String currency,
        String description,
        PaymentStatus status,
        Instant createdAt,
        Instant paidAt,
        Instant refundedAt
    ) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getProductCode(), p.getReferenceId(), p.getAmount(),
                p.getCurrency(), p.getDescription(), p.getStatus(), p.getCreatedAt(), p.getPaidAt(),
                p.getRefundedAt());
        }
    }

    public record ProductResponse(String code, int amount, String currency, String description) {
    }

    public record RefundRequest(@Size(max = 35) String description) {
    }

    public record TestAccessResponse(boolean configured, boolean sandbox, boolean accessGranted) {
    }
}
