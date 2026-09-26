package com.carmarket.payment.entity;

public enum PaymentStatus {
    /** Saved locally, waiting for the customer to pay on the P24 page. */
    PENDING,
    /** P24 notified us and the transaction was verified — money is ours. */
    PAID,
    /** Registration at P24 failed, or the payment was abandoned. */
    FAILED,
    /** Refund accepted by P24. */
    REFUNDED
}
