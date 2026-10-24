package com.carmarket.auction.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * One GET /changes page: the events as raw JSON (flat dotted before/after maps, see ChangeEventMapper), the
 * sequence to pass back as {@code after} next time (persist it only after the events are committed), and the
 * newest sequence Apibara has, for the lag.
 */
public record ApibaraChangesPage(List<JsonNode> events, long nextCursor, boolean hasMore,
                                 Long latestAvailableSequence, ApibaraQuota quota) {
}
