package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.ImportCostBreakdown;
import com.carmarket.auction.service.CurrencyExchangeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportCostCalculatorTest {

    static final String US_VIN = "5UXCR6C5XL9B12345";
    static final String JAPAN_VIN = "JM3KFBDM6M1463543";
    static final BigDecimal RATE = new BigDecimal("4.00");
    // Auction price 10,000 USD + ocean freight 1,200 USD
    private static final BigDecimal CUSTOMS_VALUE_PLN = new BigDecimal("44800.00");

    private final ImportRatesConfig rates = new ImportRatesConfig();

    @Test
    void usBuiltCarPaysNoDutyAndExciseOnTheCustomsValue() {
        ImportCostBreakdown b = calculate(US_VIN, LocalDate.of(2026, 10, 18));

        assertThat(b.getCustomsDuty()).isEqualByComparingTo("0");
        assertThat(b.getExciseRate()).isEqualByComparingTo("0.186");
        assertThat(b.getExcise()).isEqualByComparingTo(CUSTOMS_VALUE_PLN.multiply(new BigDecimal("0.186"))); // 8,332.80
        // VAT base = customs value + duty + excise = 13,283.20 USD → 3,055.14 USD
        assertThat(b.getVat()).isEqualByComparingTo("12220.56");
    }

    @Test
    void japanBuiltCarPaysTenPercentAndExciseIncludesTheDuty() {
        ImportCostBreakdown b = calculate(JAPAN_VIN, LocalDate.of(2026, 10, 18));

        assertThat(b.getCustomsDuty()).isEqualByComparingTo("4480.00");
        assertThat(b.getExcise()).isEqualByComparingTo(new BigDecimal("49280.00").multiply(new BigDecimal("0.186"))); // 9,166.08
        // VAT base = 11,200 + 1,120 + 2,291.52 = 14,611.52 USD → 3,360.65 USD
        assertThat(b.getVat()).isEqualByComparingTo("13442.60");
    }

    @Test
    void missingVinPaysTheStandardDuty() {
        assertThat(calculate(null, LocalDate.of(2026, 10, 18)).getCustomsDuty()).isEqualByComparingTo("4480.00");
        assertThat(calculate("5UX", LocalDate.of(2026, 10, 18)).getCustomsDuty()).isEqualByComparingTo("4480.00");
    }

    @Test
    void usBuiltCarIsBackToTenPercentAfterThePreferenceEnds() {
        assertThat(calculate(US_VIN, LocalDate.of(2029, 12, 31)).getCustomsDuty()).isEqualByComparingTo("0");
        assertThat(calculate(US_VIN, LocalDate.of(2030, 1, 1)).getCustomsDuty()).isEqualByComparingTo("4480.00");
    }

    @Test
    void maskedCopartVinsAndUsRegionsAreRecognised() {
        assertThat(CustomsDutyResolver.isUsBuilt("5UXCR6C5XL9******")).isTrue();
        assertThat(CustomsDutyResolver.isUsBuilt("1FTFW1E50MF******")).isTrue();
        assertThat(CustomsDutyResolver.isUsBuilt("4t1bf1fk5cu123456")).isTrue();
        assertThat(CustomsDutyResolver.isUsBuilt("2T3P1RFV8MW******")).isFalse(); // Canada
        assertThat(CustomsDutyResolver.isUsBuilt("WBA5R1C05LF******")).isFalse(); // Germany
        assertThat(CustomsDutyResolver.isUsBuilt("5UXCR6C5XL9B1234")).isFalse(); // 16 characters
        assertThat(CustomsDutyResolver.isUsBuilt("5UXCR6C5IL9B12345")).isFalse(); // I is not allowed
    }

    static CustomsDutyResolver dutyResolver(ImportRatesConfig rates, LocalDate today) {
        ZoneId zone = ZoneId.of("Europe/Warsaw");
        return new CustomsDutyResolver(rates, Clock.fixed(today.atStartOfDay(zone).toInstant(), zone));
    }

    static CurrencyExchangeService exchange() {
        CurrencyExchangeService exchange = mock(CurrencyExchangeService.class);
        when(exchange.getUsdToPlnRate()).thenReturn(RATE);
        return exchange;
    }

    private ImportCostBreakdown calculate(String vin, LocalDate today) {
        ImportCostCalculator calculator = new ImportCostCalculator(rates, exchange(), dutyResolver(rates, today));
        AuctionLot lot = AuctionLot.builder()
            .vin(vin).lotNumber("12345678").source(AuctionLot.AuctionSource.values()[0])
            .auctionPrice(new BigDecimal("10000")).engineCapacity(2500).fuelType(AuctionLot.FuelType.PETROL)
            .auctionLocation("IL")
            .build();
        return calculator.calculate(lot, CalculationInput.builder().build()).getBreakdown();
    }
}
