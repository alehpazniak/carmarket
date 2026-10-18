package com.carmarket.auction.dto;

import com.carmarket.auction.entity.AuctionLot;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaxBidRequest {
    @NotNull
    private BigDecimal budgetPln;

    private BigDecimal estimatedRepairCostPln;

    /** Falls back to the lot's own value when present; usually needed since most synced lots lack it. */
    private Integer engineCapacityCm3;

    /** Falls back to the lot's own value when present; usually needed since most synced lots lack it. */
    private AuctionLot.FuelType fuelType;

    /**
     * The car's VIN (may be masked), for the customs duty by country of manufacture. Only used when the
     * path's identifier isn't a VIN itself (e.g. a lot number); missing → standard duty rate.
     */
    private String vin;

    /** SUVs cost more to ship — drives which flat shipping-delivery cost applies. Defaults to false. */
    private Boolean suv;
}
