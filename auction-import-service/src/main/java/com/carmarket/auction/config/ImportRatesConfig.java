package com.carmarket.auction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    /** Duty on US-built cars (Regulation (EU) 2026/1455), see CustomsDutyResolver. */
    private BigDecimal usOriginDutyRate = new BigDecimal("0.00");
    /** Last day of the US-origin preference; after it US-built cars pay customsDutyRate again. */
    private LocalDate usOriginPreferenceUntil = LocalDate.of(2029, 12, 31);
    /**
     * Flat international shipping/delivery cost (US port → Poland) not covered by Apibara's
     * auction-to-port quote, which only prices the domestic US trucking leg. SUVs cost more to
     * ship (bigger, heavier) than the standard rate covering sedans/hatchbacks/etc.
     */
    private BigDecimal shippingDeliveryUsd = new BigDecimal("1000");
    private BigDecimal shippingDeliverySuvUsd = new BigDecimal("1500");
}
