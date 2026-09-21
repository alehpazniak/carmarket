package com.carmarket.auction.service.calculator;

import com.carmarket.auction.entity.AuctionLot;

import java.math.BigDecimal;

/**
 * Polish excise duty ("akcyza") rates for imported passenger cars, based on engine
 * displacement and drivetrain — not year/Euro-standard, which the duty does not depend on.
 * <p>
 * Rates: up to 2000cm³ 3.1%, above 2000cm³ 18.6%; full hybrids (HEV/MHEV) get 1.55% up to
 * 2000cm³ and 9.3% from 2000cm³ to 3500cm³; plug-in hybrids up to 2000cm³, fully electric and
 * hydrogen vehicles are exempt (0%) until the end of 2029. Cases the source rules don't cover
 * (missing engine capacity, hybrids/PHEVs above the stated bands) fall back to the standard
 * petrol/diesel table rather than guessing a preferential rate, so the estimate never
 * understates the buyer's cost.
 */
public final class ExciseRateResolver {

    public static final BigDecimal STANDARD_LOW = new BigDecimal("0.031");
    public static final BigDecimal STANDARD_HIGH = new BigDecimal("0.186");
    public static final BigDecimal HYBRID_LOW = new BigDecimal("0.0155");
    public static final BigDecimal HYBRID_MID = new BigDecimal("0.093");
    public static final BigDecimal EXEMPT = BigDecimal.ZERO;

    private static final int LOW_CAP_CM3 = 2000;
    private static final int HYBRID_MID_CAP_CM3 = 3500;

    private ExciseRateResolver() {
    }

    public static BigDecimal resolve(Integer engineCapacityCm3, AuctionLot.FuelType fuelType) {
        if (fuelType == AuctionLot.FuelType.ELECTRIC) {
            return EXEMPT;
        }

        boolean upTo2000 = engineCapacityCm3 != null && engineCapacityCm3 <= LOW_CAP_CM3;
        boolean upTo3500 = engineCapacityCm3 != null && engineCapacityCm3 <= HYBRID_MID_CAP_CM3;

        if (fuelType == AuctionLot.FuelType.PLUGIN_HYBRID && upTo2000) {
            return EXEMPT;
        }

        if (fuelType == AuctionLot.FuelType.HYBRID) {
            if (upTo2000) return HYBRID_LOW;
            if (upTo3500) return HYBRID_MID;
            return STANDARD_HIGH;
        }

        // PETROL, DIESEL, PLUGIN_HYBRID above 2000cm³, or unknown fuel type: standard table.
        return upTo2000 ? STANDARD_LOW : STANDARD_HIGH;
    }
}
