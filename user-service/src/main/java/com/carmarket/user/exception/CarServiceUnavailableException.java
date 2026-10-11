package com.carmarket.user.exception;

/** car-service couldn't be asked about a listing (down, no instance, timeout, 5xx). Mapped to 503. */
public class CarServiceUnavailableException extends RuntimeException {

    public CarServiceUnavailableException(String message) {
        super(message);
    }
}
