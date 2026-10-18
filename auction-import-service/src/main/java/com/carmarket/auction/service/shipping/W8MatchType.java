package com.carmarket.auction.service.shipping;

/**
 * How a location matched W8's towing table: the same auction and yard; the same yard at another auction
 * (W8 prices by location); or the highest price per port in the state (never understates).
 */
public enum W8MatchType {
    EXACT, OTHER_AUCTION, STATE_ESTIMATE
}
