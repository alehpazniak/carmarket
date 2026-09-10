package com.carmarket.auction.service.calculator;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Result of solving "how much can I bid for the car" given a fixed total budget: shipping,
 * excise, customs duty and VAT all grow with the car's price, so the max bid is the price
 * at which car price + all of those + the repair estimate exactly exhausts the budget.
 */
@Data
@Builder
public class MaxBidResult {
    private BigDecimal budgetPln;
    private BigDecimal estimatedRepairCostPln;
    private BigDecimal shippingCostUsd;
    private BigDecimal shippingCostPln;
    private BigDecimal shippingDeliveryUsd;
    private BigDecimal shippingDeliveryPln;
    private BigDecimal exchangeRate;
    private BigDecimal exciseRate;
    private BigDecimal customsDutyRate;
    private BigDecimal vatRate;
    private BigDecimal excise;
    private BigDecimal customsDuty;
    private BigDecimal vat;
    private BigDecimal maxCarPricePln;
    private BigDecimal maxCarPriceUsd;
    /** false when the budget doesn't even cover shipping + repair for a free car. */
    private boolean budgetSufficient;
}
