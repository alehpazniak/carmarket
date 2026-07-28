package com.carmarket.auction.dto;

import com.carmarket.auction.entity.ImportCalculation;
import com.carmarket.auction.entity.ImportCostBreakdown;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ImportCalculationResponse {
    private BigDecimal auctionPrice;
    private BigDecimal auctionFee;
    private BigDecimal usDelivery;
    private BigDecimal oceanFreight;
    private BigDecimal euPortFee;
    private BigDecimal excise;
    private BigDecimal vat;
    private BigDecimal customsClearance;
    private BigDecimal euDelivery;
    private BigDecimal totalPln;
    private BigDecimal totalUsd;
    private BigDecimal estimatedRepairCostPln;
    private BigDecimal targetSalePricePln;
    private BigDecimal estimatedProfitPln;
    private BigDecimal profitMarginPercent;
    private String profitRating;

    public static ImportCalculationResponse from(ImportCalculation calc) {
        ImportCostBreakdown b = calc.getBreakdown();
        return ImportCalculationResponse.builder()
            .auctionPrice(b.getAuctionPrice())
            .auctionFee(b.getAuctionFee())
            .usDelivery(b.getUsDelivery())
            .oceanFreight(b.getOceanFreight())
            .euPortFee(b.getEuPortFee())
            .excise(b.getExcise())
            .vat(b.getVat())
            .customsClearance(b.getCustomsClearance())
            .euDelivery(b.getEuDelivery())
            .totalPln(b.getTotalPln())
            .totalUsd(b.getTotalUsd())
            .estimatedRepairCostPln(calc.getEstimatedRepairCostPln())
            .targetSalePricePln(calc.getTargetSalePricePln())
            .estimatedProfitPln(calc.getEstimatedProfitPln())
            .profitMarginPercent(calc.getProfitMarginPercent())
            .profitRating(calc.getProfitRating().name())
            .build();
    }
}
