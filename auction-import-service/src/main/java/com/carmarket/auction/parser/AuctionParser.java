package com.carmarket.auction.parser;

import com.carmarket.auction.entity.AuctionLot;

import java.util.List;

public interface AuctionParser {
    List<AuctionLot> parseLiveLots();

    /** The live lots split by API response page; each page is upserted in its own transaction. */
    default List<List<AuctionLot>> parseLiveLotPages() {
        List<AuctionLot> lots = parseLiveLots();
        return lots.isEmpty() ? List.of() : List.of(lots);
    }
    AuctionLot parseLotDetail(String lotNumber);
    boolean supports(AuctionSource source);

    enum AuctionSource { COPART, IAAI }
}
