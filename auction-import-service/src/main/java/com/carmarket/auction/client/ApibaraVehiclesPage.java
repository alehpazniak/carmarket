package com.carmarket.auction.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * One GET /vehicles page for the sync: each vehicle as raw JSON (kept in auction_lots.raw_data),
 * the cursor metadata and the quota headers of the response.
 */
public record ApibaraVehiclesPage(List<JsonNode> vehicles, ApibaraResponse.Meta meta, ApibaraQuota quota) {

    /** The cursor for the next page, passed back to Apibara as-is; null on the last page. */
    public String nextCursor() {
        return meta == null || meta.nextCursor() == null || meta.nextCursor().isBlank() ? null : meta.nextCursor();
    }
}
