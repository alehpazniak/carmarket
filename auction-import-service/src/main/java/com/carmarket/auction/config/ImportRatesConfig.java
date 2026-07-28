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
}
