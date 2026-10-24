package com.carmarket.auction.client;

/**
 * Apibara refused the request itself (HTTP 400, 404 or 422), e.g. a cursor it no longer accepts. Unlike other
 * failures this one is about our parameters, so the sync can drop the cursor and start the pass again.
 */
public class ApibaraRejectedException extends ApibaraUnavailableException {

    private final int status;

    public ApibaraRejectedException(int status) {
        super("Apibara rejected the request: HTTP " + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
