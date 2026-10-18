package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.config.W8ShippingConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.ImportCalculation;
import com.carmarket.auction.entity.ImportCostBreakdown;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.repository.W8TowingRateRepository;
import com.carmarket.auction.service.CurrencyExchangeService;
import com.carmarket.auction.service.shipping.W8MatchType;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import com.carmarket.auction.service.shipping.W8ShippingQuoteServiceTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportCostCalculatorTest {

    static final String US_VIN = "5UXCR6C5XL9B12345";
    static final String JAPAN_VIN = "JM3KFBDM6M1463543";
    static final BigDecimal RATE = new BigDecimal("4.00");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    private final ImportRatesConfig rates = new ImportRatesConfig();
    private final W8TowingRateRepository w8Rates = mock(W8TowingRateRepository.class);
    private final W8ShippingQuoteService w8 = new W8ShippingQuoteService(w8Rates, new W8ShippingConfig(),
        W8ShippingQuoteServiceTest.exchange());

    /*
     * Without a W8 quote (the mocked table is empty): auction price 10,000 + fee 750 + US delivery 450
     * (IL = Midwest) + ocean freight 1,200 = customs value 12,400 USD = 49,600 PLN.
     */

    @Test
    void usBuiltCarPaysNoDutyAndExciseOnTheCustomsValue() {
        ImportCostBreakdown b = breakdown(US_VIN, TODAY);

        assertThat(b.getCustomsDuty()).isEqualByComparingTo("0");
        assertThat(b.getExciseRate()).isEqualByComparingTo("0.186");
        assertThat(b.getExcise()).isEqualByComparingTo("9225.60"); // 12,400 × 18.6% = 2,306.40 USD
        // VAT base = customs value + duty + excise = 14,706.40 USD → 3,382.47 USD
        assertThat(b.getVat()).isEqualByComparingTo("13529.88");
    }

    @Test
    void japanBuiltCarPaysTenPercentAndExciseIncludesTheDuty() {
        ImportCostBreakdown b = breakdown(JAPAN_VIN, TODAY);

        assertThat(b.getCustomsDuty()).isEqualByComparingTo("4960.00"); // 1,240 USD
        assertThat(b.getExcise()).isEqualByComparingTo("10148.16"); // (12,400 + 1,240) × 18.6% = 2,537.04 USD
        // VAT base = 12,400 + 1,240 + 2,537.04 = 16,177.04 USD → 3,720.72 USD
        assertThat(b.getVat()).isEqualByComparingTo("14882.88");
    }

    @Test
    void missingVinPaysTheStandardDuty() {
        assertThat(breakdown(null, TODAY).getCustomsDuty()).isEqualByComparingTo("4960.00");
        assertThat(breakdown("5UX", TODAY).getCustomsDuty()).isEqualByComparingTo("4960.00");
    }

    @Test
    void usBuiltCarIsBackToTenPercentAfterThePreferenceEnds() {
        assertThat(breakdown(US_VIN, LocalDate.of(2029, 12, 31)).getCustomsDuty()).isEqualByComparingTo("0");
        assertThat(breakdown(US_VIN, LocalDate.of(2030, 1, 1)).getCustomsDuty()).isEqualByComparingTo("4960.00");
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

    /** The fallback zone used to switch on the whole Apibara display ("Sacramento (CA)") and always got EAST. */
    @Test
    void fallbackUsDeliveryReadsTheStateFromTheDisplayName() {
        assertThat(calculate(lot(US_VIN, "Sacramento (CA)"), TODAY).getBreakdown().getUsDelivery()).isEqualByComparingTo("550");
        assertThat(calculate(lot(US_VIN, "IL - CHICAGO SOUTH"), TODAY).getBreakdown().getUsDelivery()).isEqualByComparingTo("450");
    }

    /** W8's worked example: customs value = price + auction fees + towing + ocean freight. */
    @Test
    void w8ExampleCustomsValue() {
        assertThat(ImportCostCalculator.customsValue(new BigDecimal("10000"), new BigDecimal("1120"),
            new BigDecimal("175"), new BigDecimal("525"))).isEqualByComparingTo("11820");
    }

    @Test
    void w8QuoteReplacesTheFlatShippingLines() {
        when(w8Rates.findByPriceListAndAuctionAndYardKey("W8-2025-26", W8Auction.COPART, "SOMERVILLE")).thenReturn(List.of(
            W8ShippingQuoteServiceTest.rate(W8Auction.COPART, "SOMERVILLE", "NJ", null, W8Port.NEW_YORK, 175)));

        ImportCalculation calc = calculate(lot(US_VIN, "Somerville (NJ)"), TODAY);
        ImportCostBreakdown b = calc.getBreakdown();

        assertThat(calc.getShippingQuote()).isNotNull();
        assertThat(calc.getShippingQuote().matchType()).isEqualTo(W8MatchType.EXACT);
        assertThat(b.getUsDelivery()).isEqualByComparingTo("175");
        assertThat(b.getOceanFreight()).isEqualByComparingTo("525");
        assertThat(b.getEuPortFee()).isEqualByComparingTo("462.25");      // forwarder 430 € × 1.075
        assertThat(b.getCustomsClearance()).isEqualByComparingTo("645.00"); // broker 100 € + scan 50 € × 4.30
        assertThat(b.getEuDelivery()).isEqualByComparingTo("2300");        // zone A
        // 10,000 + 750 + 175 + 525 + 462.25 + W8 fees 200
        assertThat(b.getTotalUsd()).isEqualByComparingTo("12112.25");
        // Customs value 10,000 + 750 + 175 + 525 = 11,450 USD; US-built: no duty, excise 2,129.70 USD
        assertThat(b.getCustomsDuty()).isEqualByComparingTo("0");
        assertThat(b.getExcise()).isEqualByComparingTo("8518.80");
    }

    static CustomsDutyResolver dutyResolver(ImportRatesConfig rates, LocalDate today) {
        ZoneId zone = ZoneId.of("Europe/Warsaw");
        return new CustomsDutyResolver(rates, Clock.fixed(today.atStartOfDay(zone).toInstant(), zone));
    }

    static CurrencyExchangeService exchange() {
        CurrencyExchangeService exchange = mock(CurrencyExchangeService.class);
        when(exchange.getUsdToPlnRate()).thenReturn(RATE);
        when(exchange.getEurToPlnRate()).thenReturn(new BigDecimal("4.30"));
        when(exchange.getEurToUsdRate()).thenReturn(new BigDecimal("1.0750"));
        return exchange;
    }

    private static AuctionLot lot(String vin, String location) {
        return AuctionLot.builder()
            .vin(vin).lotNumber("12345678").source(AuctionLot.AuctionSource.COPART)
            .auctionPrice(new BigDecimal("10000")).engineCapacity(2500).fuelType(AuctionLot.FuelType.PETROL)
            .auctionLocation(location)
            .build();
    }

    private ImportCalculation calculate(AuctionLot lot, LocalDate today) {
        ImportCostCalculator calculator = new ImportCostCalculator(rates, exchange(), dutyResolver(rates, today), w8);
        return calculator.calculate(lot, CalculationInput.builder().build());
    }

    private ImportCostBreakdown breakdown(String vin, LocalDate today) {
        return calculate(lot(vin, "IL"), today).getBreakdown();
    }
}
