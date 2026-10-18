package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.entity.AuctionLot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.carmarket.auction.service.calculator.ImportCostCalculatorTest.dutyResolver;
import static com.carmarket.auction.service.calculator.ImportCostCalculatorTest.exchange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
}
