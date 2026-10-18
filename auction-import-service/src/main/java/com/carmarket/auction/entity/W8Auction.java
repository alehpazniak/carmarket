package com.carmarket.auction.entity;

import java.util.Locale;
import java.util.Optional;

/** Auction houses in W8's towing price list ({@code w8_towing_rate.auction}). */
public enum W8Auction {
    COPART, IAAI, MANHEIM, ADESA;

    /**
     * Case-insensitive: accepts Apibara's {@code platform} values ({@code copart}, {@code iaai}) and
     * {@link AuctionLot.AuctionSource} names. Blank or unknown → empty.
     */
    public static Optional<W8Auction> from(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<W8Auction> from(AuctionLot.AuctionSource source) {
        return source == null ? Optional.empty() : from(source.name());
    }
}
