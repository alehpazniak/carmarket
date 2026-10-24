package com.carmarket.auction.client;

/**
 * GET /changes answered 410 cursor_expired: the stored sequence fell out of the 14-day replay window. The sync
 * jumps to {@link #getLatestAvailableSequence()}; the events in between are lost (the expiry job covers them).
 */
public class ApibaraCursorExpiredException extends RuntimeException {

    private final Long latestAvailableSequence;

    public ApibaraCursorExpiredException(Long latestAvailableSequence) {
        super("Apibara /changes cursor expired; latest available sequence " + latestAvailableSequence);
        this.latestAvailableSequence = latestAvailableSequence;
    }

    /** From the 410 body's meta; null when it wasn't there. */
    public Long getLatestAvailableSequence() {
        return latestAvailableSequence;
    }
}
