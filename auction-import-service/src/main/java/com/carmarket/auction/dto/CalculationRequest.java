package com.carmarket.auction.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CalculationRequest {
    @NotNull
    private BigDecimal targetSalePricePln;

    private BigDecimal estimatedRepairCostPln;
    private String destinationCountry = "PL";
}
