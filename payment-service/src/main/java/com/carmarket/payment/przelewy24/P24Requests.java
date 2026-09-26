package com.carmarket.payment.przelewy24;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Request and response bodies of the Przelewy24 REST API (https://developers.przelewy24.pl). */
public final class P24Requests {

    private P24Requests() {
    }

    /** POST /api/v1/transaction/register */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Register(
        int merchantId,
        int posId,
        String sessionId,
        int amount,
        String currency,
        String description,
        String email,
        String country,
        String language,
        String urlReturn,
        String urlStatus,
        Integer timeLimit,
        String transferLabel,
        String sign
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegisterResponse(TokenData data, Integer responseCode) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TokenData(String token) {
        }
    }

    /** PUT /api/v1/transaction/verify */
    public record Verify(
        int merchantId,
        int posId,
        String sessionId,
        int amount,
        String currency,
        long orderId,
        String sign
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifyResponse(StatusData data, Integer responseCode) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record StatusData(String status) {
        }
    }

    /** POST /api/v1/transaction/refund */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Refund(String requestId, List<RefundItem> refunds, String refundsUuid, String urlStatus) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundItem(long orderId, String sessionId, int amount, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundResponse(List<RefundResult> data, Integer responseCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundResult(Long orderId, String sessionId, Integer amount, Boolean status, String message) {
    }

    /** GET /api/v1/transaction/by/sessionId/{sessionId} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionResponse(TransactionDetails data, Integer responseCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionDetails(
        Long orderId,
        String sessionId,
        Integer status,
        Integer amount,
        String currency,
        Integer paymentMethod,
        String statement
    ) {
        public static final int STATUS_NO_PAYMENT = 0;
        /** Customer paid, transaction not verified yet. */
        public static final int STATUS_ADVANCE_PAYMENT = 1;
        public static final int STATUS_PAYMENT_MADE = 2;
        public static final int STATUS_RETURNED = 3;
    }

    /** GET /api/v1/testAccess */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestAccessResponse(Boolean data, String error) {
    }
}
