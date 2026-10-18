package com.carmarket.auction.dto;

import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.PolandZone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /** SUVs cost more to ship: the flat delivery cost, or 1/3 instead of 1/4 of a container with W8. Defaults to false. */
    private Boolean suv;

    /*
     * W8 Shipping quote inputs. They only pick a row from our own price table, so trusting the client is fine.
     */

    /** Apibara's {@code platform} (copart, iaai) or a W8 auction name; null = any auction. */
    @Pattern(regexp = "(?i)copart|iaai|manheim|adesa")
    private String auction;

    /** Apibara's {@code location.display}, e.g. "Chicago South (IL)". */
    @Size(max = 150)
    private String auctionLocation;

    private PolandZone polandZone;
    private LengthClass lengthClass;
}
