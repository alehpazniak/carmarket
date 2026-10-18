package com.carmarket.auction.service.calculator;

import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.PolandZone;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CalculationInput {
    private BigDecimal estimatedRepairCostPln;
    private BigDecimal targetSalePricePln;
    private String destinationCountry; // "PL", "DE", etc.
    /** 1/3 instead of 1/4 of a container in W8's prices. */
    private Boolean suv;
    /** W8's truck zone in Poland; null = the configured default. */
    private PolandZone polandZone;
    private LengthClass lengthClass;
}
