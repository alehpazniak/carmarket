package com.carmarket.auction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "import.rates")
public class ImportRatesConfig {
    private BigDecimal oceanFreightUsd = new BigDecimal("1200");
    private BigDecimal euPortFeeUsd = new BigDecimal("300");
    private BigDecimal customsClearancePln = new BigDecimal("1500");
    private BigDecimal euDeliveryPln = new BigDecimal("800");
    private BigDecimal vatRate = new BigDecimal("0.23");
    /** Customs duty ("cło") on cars imported from outside the EU — common external tariff. */
    private BigDecimal customsDutyRate = new BigDecimal("0.10");
    /**
     * Flat international shipping/delivery cost (US port → Poland) not covered by Apibara's
     * auction-to-port quote, which only prices the domestic US trucking leg. SUVs cost more to
     * ship (bigger, heavier) than the standard rate covering sedans/hatchbacks/etc.
     */
    private BigDecimal shippingDeliveryUsd = new BigDecimal("1000");
    private BigDecimal shippingDeliverySuvUsd = new BigDecimal("1500");
}
