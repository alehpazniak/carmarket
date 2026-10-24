package com.carmarket.auction.service;

import com.carmarket.auction.entity.AuctionLot;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * SHA-256 (hex) over every field a parser maps, normalized so that equal data gives an equal hash
 * (decimals without trailing zeros, instants in UTC). raw_data is left out: Apibara's JSON carries
 * countdowns that change on every request, which would turn every lot into an update.
 */
public final class LotContentHash {

    private static final char SEPARATOR = '\u001f';
    private static final String NULL = "␀";

    private LotContentHash() {
    }

    public static String of(AuctionLot lot) {
        List<Object> fields = List.of(
            Objects.toString(lot.getSource(), NULL),
            Objects.toString(lot.getLotNumber(), NULL),
            Objects.toString(lot.getVin(), NULL),
            Objects.toString(lot.getMake(), NULL),
            Objects.toString(lot.getModel(), NULL),
            Objects.toString(lot.getYear(), NULL),
            decimal(lot.getAuctionPrice()),
            decimal(lot.getBuyNowPrice()),
            Objects.toString(lot.getCurrency(), NULL),
            Objects.toString(lot.getDamageType(), NULL),
            Objects.toString(lot.getPrimaryDamage(), NULL),
            Objects.toString(lot.getSecondaryDamage(), NULL),
            Objects.toString(lot.getOdometer(), NULL),
            Objects.toString(lot.getOdometerUnit(), NULL),
            Objects.toString(lot.getEngineCapacity(), NULL),
            Objects.toString(lot.getFuelType(), NULL),
            Objects.toString(lot.getTransmission(), NULL),
            Objects.toString(lot.getAuctionLocation(), NULL),
            Objects.toString(lot.getAuctionDate(), NULL), // Instant.toString() is always UTC
            Objects.toString(lot.getSaleDate(), NULL),
            Objects.toString(lot.getStatus(), NULL),
            lot.getImageUrls() == null ? NULL : String.join("\n", lot.getImageUrls()),
            Objects.toString(lot.getVehicleType(), NULL),
            Objects.toString(lot.getBodyStyle(), NULL),
            decimal(lot.getSoldPrice()),
            Objects.toString(lot.getEndedAt(), NULL)
        );
        StringBuilder text = new StringBuilder();
        for (Object field : fields) {
            text.append(field).append(SEPARATOR);
        }
        return sha256(text.toString());
    }

    private static String decimal(BigDecimal value) {
        return value == null ? NULL : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
