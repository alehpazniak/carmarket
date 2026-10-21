package com.carmarket.auction.dto;

import com.carmarket.auction.entity.AuctionLot;

import java.time.Instant;
import java.util.UUID;

/** Payload of auction.lot.updated, published after the sync's transaction commits. */
public record AuctionLotChangedEvent(
    UUID id,
    AuctionLot.AuctionSource source,
    String lotNumber,
    AuctionLot.LotStatus status,
    Instant auctionDate,
    ChangeType changeType
) {

    public enum ChangeType { CREATED, UPDATED }

    public static AuctionLotChangedEvent of(AuctionLot lot, ChangeType changeType) {
        return new AuctionLotChangedEvent(lot.getId(), lot.getSource(), lot.getLotNumber(), lot.getStatus(),
            lot.getAuctionDate(), changeType);
    }
}
