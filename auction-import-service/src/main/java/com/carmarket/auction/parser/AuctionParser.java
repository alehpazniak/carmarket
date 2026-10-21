package com.carmarket.auction.parser;

import com.carmarket.auction.entity.AuctionLot;

import java.util.List;
import java.util.function.Consumer;

public interface AuctionParser {
    List<AuctionLot> parseLiveLots();

    /**
     * Hands the live lots over one API response page at a time, as soon as each is fetched, so the caller
     * can upsert a page (one transaction) before the next request; a failure later keeps the earlier pages.
     */
    default void forEachLiveLotPage(Consumer<List<AuctionLot>> pageConsumer) {
        List<AuctionLot> lots = parseLiveLots();
        if (!lots.isEmpty()) {
            pageConsumer.accept(lots);
        }
    }
    AuctionLot parseLotDetail(String lotNumber);
    boolean supports(AuctionSource source);

    enum AuctionSource { COPART, IAAI }
}
