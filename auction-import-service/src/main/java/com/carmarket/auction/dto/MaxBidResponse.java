package com.carmarket.auction.dto;

import com.carmarket.auction.service.calculator.MaxBidResult;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MaxBidResponse {
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
    private boolean budgetSufficient;

    public static MaxBidResponse from(MaxBidResult r) {
        return MaxBidResponse.builder()
            .budgetPln(r.getBudgetPln())
            .estimatedRepairCostPln(r.getEstimatedRepairCostPln())
            .shippingCostUsd(r.getShippingCostUsd())
            .shippingCostPln(r.getShippingCostPln())
            .shippingDeliveryUsd(r.getShippingDeliveryUsd())
            .shippingDeliveryPln(r.getShippingDeliveryPln())
            .exchangeRate(r.getExchangeRate())
            .exciseRate(r.getExciseRate())
            .customsDutyRate(r.getCustomsDutyRate())
            .vatRate(r.getVatRate())
            .excise(r.getExcise())
            .customsDuty(r.getCustomsDuty())
            .vat(r.getVat())
            .maxCarPricePln(r.getMaxCarPricePln())
            .maxCarPriceUsd(r.getMaxCarPriceUsd())
            .budgetSufficient(r.isBudgetSufficient())
            .build();
    }
}
