package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.config.W8ShippingConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.repository.W8TowingRateRepository;
import com.carmarket.auction.service.shipping.W8QuoteRequest;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import com.carmarket.auction.service.shipping.W8ShippingQuoteServiceTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.carmarket.auction.service.calculator.ImportCostCalculatorTest.dutyResolver;
import static com.carmarket.auction.service.calculator.ImportCostCalculatorTest.exchange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaxBidCalculatorTest {

    private final ImportRatesConfig rates = new ImportRatesConfig();
    private final MaxBidCalculator calculator =
        new MaxBidCalculator(rates, exchange(), dutyResolver(rates, LocalDate.of(2026, 10, 18)));

    @ParameterizedTest
    @ValueSource(strings = {ImportCostCalculatorTest.US_VIN, ImportCostCalculatorTest.JAPAN_VIN})
    void totalCostAtTheMaxBidMatchesTheBudget(String vin) {
        MaxBidResult r = calculator.calculate(MaxBidInput.builder()
            .budgetPln(new BigDecimal("100000"))
            .estimatedRepairCostPln(new BigDecimal("8000"))
            .shippingCostUsd(new BigDecimal("900"))
            .engineCapacityCm3(2500).fuelType(AuctionLot.FuelType.PETROL)
            .vin(vin)
            .build());

        assertThat(r.isBudgetSufficient()).isTrue();
        assertThat(r.getCustomsDutyRate()).isEqualByComparingTo(vin.startsWith("5") ? "0" : "0.10");
        BigDecimal total = r.getMaxCarPricePln()
            .add(r.getShippingCostPln()).add(r.getShippingDeliveryPln())
            .add(r.getCustomsDuty()).add(r.getExcise()).add(r.getVat())
            .add(r.getEstimatedRepairCostPln());
        assertThat(total.doubleValue()).isCloseTo(100000, within(1.0));
        // Excise includes the duty
        BigDecimal customsValue = r.getMaxCarPricePln().add(r.getShippingCostPln()).add(r.getShippingDeliveryPln());
        assertThat(r.getExcise().doubleValue())
            .isCloseTo(customsValue.add(r.getCustomsDuty()).multiply(new BigDecimal("0.186")).doubleValue(), within(0.01));
    }

    /** Somerville NJ sedan: $700 to the EU border is in the customs value; Rotterdam, truck and W8 fees are not. */
    @ParameterizedTest
    @ValueSource(strings = {ImportCostCalculatorTest.US_VIN, ImportCostCalculatorTest.JAPAN_VIN})
    void withAW8QuoteTheTotalStillMatchesTheBudget(String vin) {
        W8ShippingQuote quote = somervilleQuote();

        MaxBidResult r = calculator.calculate(MaxBidInput.builder()
            .budgetPln(new BigDecimal("100000"))
            .estimatedRepairCostPln(new BigDecimal("8000"))
            .shippingCostUsd(new BigDecimal("900")) // ignored with a quote
            .w8Quote(quote)
            .engineCapacityCm3(2500).fuelType(AuctionLot.FuelType.PETROL)
            .vin(vin)
            .build());

        assertThat(r.getW8Quote()).isSameAs(quote);
        assertThat(r.getShippingCostUsd()).isEqualByComparingTo("700");
        assertThat(r.getShippingCostPln()).isEqualByComparingTo("2800.00");
        // (430 + 100 + 50) € × 4.30 + 2,300 zł + $200 W8 fees × 4.00
        assertThat(r.getShippingDeliveryPln()).isEqualByComparingTo("5594.00");
        BigDecimal total = r.getMaxCarPricePln()
            .add(r.getShippingCostPln()).add(r.getShippingDeliveryPln())
            .add(r.getCustomsDuty()).add(r.getExcise()).add(r.getVat())
            .add(r.getEstimatedRepairCostPln());
        assertThat(total.doubleValue()).isCloseTo(100000, within(1.0));
        // Only car price + towing + ocean freight are taxed
        BigDecimal customsValue = r.getMaxCarPricePln().add(r.getShippingCostPln());
        assertThat(r.getCustomsDuty().doubleValue())
            .isCloseTo(customsValue.multiply(r.getCustomsDutyRate()).doubleValue(), within(0.01));
        assertThat(r.getExcise().doubleValue())
            .isCloseTo(customsValue.add(r.getCustomsDuty()).multiply(new BigDecimal("0.186")).doubleValue(), within(0.01));
    }

    private static W8ShippingQuote somervilleQuote() {
        W8TowingRateRepository repo = mock(W8TowingRateRepository.class);
        when(repo.findByPriceListAndAuctionAndYardKey(any(), any(), any())).thenReturn(List.of(
            W8ShippingQuoteServiceTest.rate(W8Auction.COPART, "SOMERVILLE", "NJ", null, W8Port.NEW_YORK, 175)));
        return new W8ShippingQuoteService(repo, new W8ShippingConfig(), W8ShippingQuoteServiceTest.exchange())
            .quote(new W8QuoteRequest(W8Auction.COPART, "Somerville (NJ)", false, null, null)).orElseThrow();
    }
}
