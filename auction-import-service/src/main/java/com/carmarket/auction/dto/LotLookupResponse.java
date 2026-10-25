package com.carmarket.auction.dto;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/auctions/lots/lookup: what the search field on /aukcje recognised and the lots of ours that match.
 *
 * @param kind       LOT_NUMBER, VIN, COPART_URL, IAAI_URL, URL (another link: only Apibara's resolver may know
 *                   it) or UNKNOWN (nothing to look up)
 * @param ids        our lots, best first (LIVE, then the latest auction date); empty when we don't have the car
 * @param identifier what the live page /aukcje/pojazd/:identifier can open directly (the lot number or VIN,
 *                   upper case); null for links, which go through Apibara's resolver first
 */
public record LotLookupResponse(Kind kind, List<UUID> ids, String identifier) {

    public enum Kind { LOT_NUMBER, VIN, COPART_URL, IAAI_URL, URL, UNKNOWN }
}
