package com.carmarket.auction.service.shipping;

import com.carmarket.auction.entity.W8Auction;

/**
 * @param auction     null when unknown (then any auction matches)
 * @param location    any format {@link W8LocationKey#parse} understands
 * @param polandZone  null = the configured default zone
 * @param lengthClass null = {@link LengthClass#STANDARD}
 */
public record W8QuoteRequest(W8Auction auction, String location, Boolean suv, PolandZone polandZone, LengthClass lengthClass) {
}
