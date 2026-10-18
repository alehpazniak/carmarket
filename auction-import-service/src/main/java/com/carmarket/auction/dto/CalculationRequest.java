package com.carmarket.auction.dto;

import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.PolandZone;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CalculationRequest {
    @NotNull
    private BigDecimal targetSalePricePln;

    private BigDecimal estimatedRepairCostPln;
    private String destinationCountry = "PL";
    /** SUVs ship 3 per container instead of 4 (W8 prices). */
    private Boolean suv;
    private PolandZone polandZone;
    private LengthClass lengthClass;
}
