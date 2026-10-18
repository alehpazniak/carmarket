package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.service.CurrencyExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Solves for the maximum price a buyer can bid on a US auction lot given a fixed total budget.
 * <p>
 * "Shipping" here is two line items: the Apibara auction-to-port quote (domestic US trucking,
 * varies per vehicle) plus a flat shipping-delivery cost covering the leg Apibara doesn't price
 * (US port → Poland) — $1500 for SUVs, $1000 otherwise, since SUVs cost more to ship.
 * <p>
 * Duty is levied on the "customs value" (car price + shipping), excise on the customs value plus the duty
 * (Polish excise act: for imports the base includes the duty), and VAT on all three — so growing the car price
 * grows every other cost too. Rather than guessing and iterating, this solves the linear system directly:
 * <pre>
 *   budget      = carPrice + shipping + customsDuty + excise + vat + repair
 *   customsValue = carPrice + shipping
 *   customsDuty = customsValue * dutyRate
 *   excise      = (customsValue + customsDuty) * exciseRate
 *   vat         = (customsValue + customsDuty + excise) * vatRate
 * </pre>
 * Each tax multiplies what came before, so {@code budget - repair = customsValue * M} with
 * {@code M = (1 + dutyRate) * (1 + exciseRate) * (1 + vatRate)}, and
 * {@code carPrice = (budget - repair) / M - shipping}. The duty rate depends on the country of manufacture
 * (see {@link CustomsDutyResolver}).
 */
@Component
@RequiredArgsConstructor
public class MaxBidCalculator {

    private final ImportRatesConfig rates;
    private final CurrencyExchangeService exchangeService;
    private final CustomsDutyResolver dutyResolver;

    public MaxBidResult calculate(MaxBidInput input) {
        BigDecimal repair = input.getEstimatedRepairCostPln() != null
            ? input.getEstimatedRepairCostPln()
            : BigDecimal.ZERO;
        BigDecimal shippingUsd = input.getShippingCostUsd() != null
            ? input.getShippingCostUsd()
            : rates.getOceanFreightUsd();
        BigDecimal shippingDeliveryUsd = Boolean.TRUE.equals(input.getSuv())
            ? rates.getShippingDeliverySuvUsd()
            : rates.getShippingDeliveryUsd();

        BigDecimal exchangeRate = exchangeService.getUsdToPlnRate();
        BigDecimal shippingPln = shippingUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingDeliveryPln = shippingDeliveryUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalShippingPln = shippingPln.add(shippingDeliveryPln);

        BigDecimal dutyRate = dutyResolver.resolve(input.getVin());
        BigDecimal vatRate = rates.getVatRate();
        BigDecimal exciseRate = ExciseRateResolver.resolve(input.getEngineCapacityCm3(), input.getFuelType());

        // M = (1+d)(1+e)(1+v): customs value → customs value + duty + excise + VAT
        BigDecimal multiplier = BigDecimal.ONE.add(dutyRate)
            .multiply(BigDecimal.ONE.add(exciseRate))
            .multiply(BigDecimal.ONE.add(vatRate));

        BigDecimal maxCustomsValue = input.getBudgetPln().subtract(repair)
            .divide(multiplier, 10, RoundingMode.HALF_UP);
        BigDecimal carPrice = maxCustomsValue.subtract(totalShippingPln);

        boolean sufficient = carPrice.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal maxCarPricePln = sufficient
            ? carPrice.setScale(2, RoundingMode.DOWN)
            : BigDecimal.ZERO;

        BigDecimal customsValue = maxCarPricePln.add(totalShippingPln);
        BigDecimal customsDuty = customsValue.multiply(dutyRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal excise = customsValue.add(customsDuty).multiply(exciseRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = customsValue.add(customsDuty).add(excise)
            .multiply(vatRate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal maxCarPriceUsd = maxCarPricePln.divide(exchangeRate, 2, RoundingMode.HALF_UP);

        return MaxBidResult.builder()
            .budgetPln(input.getBudgetPln())
            .estimatedRepairCostPln(repair)
            .shippingCostUsd(shippingUsd)
            .shippingCostPln(shippingPln)
            .shippingDeliveryUsd(shippingDeliveryUsd)
            .shippingDeliveryPln(shippingDeliveryPln)
            .exchangeRate(exchangeRate)
            .exciseRate(exciseRate)
            .customsDutyRate(dutyRate)
            .vatRate(vatRate)
            .excise(excise)
            .customsDuty(customsDuty)
            .vat(vat)
            .maxCarPricePln(maxCarPricePln)
            .maxCarPriceUsd(maxCarPriceUsd)
            .budgetSufficient(sufficient)
            .build();
    }
}
