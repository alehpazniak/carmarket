package com.carmarket.auction.client;

/** Any failed sync request other than a 429: not configured, I/O error, non-2xx status or an unreadable body. */
public class ApibaraUnavailableException extends RuntimeException {

    public ApibaraUnavailableException(String message) {
        super(message);
    }

    public ApibaraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
