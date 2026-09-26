package com.carmarket.payment.przelewy24;

/** Przelewy24 rejected a call or was unreachable. */
public class Przelewy24Exception extends RuntimeException {

    public Przelewy24Exception(String message) {
        super(message);
    }

    public Przelewy24Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
