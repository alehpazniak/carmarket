package com.carmarket.auction.service.calculator;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CalculationInput {
    private BigDecimal estimatedRepairCostPln;
    private BigDecimal targetSalePricePln;
    private String destinationCountry; // "PL", "DE", etc.
}
