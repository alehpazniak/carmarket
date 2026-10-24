package com.carmarket.auction.sync;

import com.carmarket.auction.entity.AuctionLot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * The result of an auction from one /changes event: SOLD, UNSOLD or REMOVED. price, saleDate (UTC) and endedAt are
 * null when the event didn't carry them; AuctionLotService.applyOutcomes keeps what the lot already has then.
 */
public record LotOutcome(AuctionLot.AuctionSource source, String lotNumber, AuctionLot.LotStatus status,
                         BigDecimal price, LocalDateTime saleDate, Instant endedAt) {
}
