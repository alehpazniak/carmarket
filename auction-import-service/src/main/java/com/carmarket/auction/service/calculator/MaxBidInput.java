package com.carmarket.auction.service.calculator;

import com.carmarket.auction.entity.AuctionLot;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MaxBidInput {
    /** Total money the buyer has available, in PLN. */
    private BigDecimal budgetPln;
    /** Repair cost the buyer estimates themselves — always a manual input, never computed. */
    private BigDecimal estimatedRepairCostPln;
    /** Shipping (auction → EU port) cost in USD, resolved from the Apibara shipping endpoint. */
    private BigDecimal shippingCostUsd;
    /** Overrides the lot's own engine capacity when the lot doesn't have it (usually the case). */
    private Integer engineCapacityCm3;
    /** Overrides the lot's own fuel type when the lot doesn't have it (usually the case). */
    private AuctionLot.FuelType fuelType;
    /** SUVs cost more to ship than the standard rate — drives which flat shipping-delivery cost applies. */
    private Boolean suv;
}
