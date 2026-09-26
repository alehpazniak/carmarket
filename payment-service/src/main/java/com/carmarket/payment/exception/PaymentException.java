package com.carmarket.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Business error with the HTTP status the API should answer with. */
@Getter
public class PaymentException extends RuntimeException {

    private final HttpStatus status;

    public PaymentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static PaymentException notFound() {
        return new PaymentException(HttpStatus.NOT_FOUND, "Payment not found");
    }

    public static PaymentException notConfigured() {
        return new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Payments are not configured");
    }
}
