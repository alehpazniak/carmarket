package com.carmarket.importorder.entity;

/**
 * Shipping progress, in the usual order (Polish labels live in the frontend). Any status can be
 * set at any time so mistakes can be corrected; the event timeline keeps the history.
 * ON_HOLD and CANCELLED are outside the main sequence.
 */
public enum ImportOrderStatus {
    WON,
    AUCTION_PAID,
    PICKED_UP,
    AT_US_WAREHOUSE,
    LOADED,
    IN_TRANSIT,
    ARRIVED_EU_PORT,
    CUSTOMS_CLEARED,
    IN_TRANSIT_PL,
    AT_DEALER,
    HANDED_OVER,
    ON_HOLD,
    CANCELLED
}
