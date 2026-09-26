package com.carmarket.payment.przelewy24;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Body POSTed by Przelewy24 to urlStatus after a successful payment. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record P24Notification(
    Integer merchantId,
    Integer posId,
    String sessionId,
    Integer amount,
    Integer originAmount,
    String currency,
    Long orderId,
    Integer methodId,
    String statement,
    String sign
) {
}
