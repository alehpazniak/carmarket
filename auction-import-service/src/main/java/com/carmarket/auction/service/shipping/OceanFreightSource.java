package com.carmarket.auction.service.shipping;

/**
 * Where an ocean freight price came from: W8's price list, derived from the other container share
 * (third = quarter x 4/3), or the configured fallback for ports W8 hasn't priced yet.
 */
public enum OceanFreightSource {
    W8_PRICE_LIST, DERIVED, ESTIMATE
}
