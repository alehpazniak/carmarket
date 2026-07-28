package com.carmarket.auction.parser;

import com.carmarket.auction.entity.AuctionLot;

import java.util.List;

public interface AuctionParser {
    List<AuctionLot> parseLiveLots();
    AuctionLot parseLotDetail(String lotNumber);
    boolean supports(AuctionSource source);

    enum AuctionSource { COPART, IAAI }
}
