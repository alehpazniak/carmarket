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
 * Shipping, excise and customs duty are all levied on the "customs value" (car price + shipping),
 * and VAT is levied on top of that plus duty and excise — so growing the car price grows every
 * other cost too. Rather than guessing and iterating, this solves the linear system directly:
 * <pre>
 *   budget = carPrice + shipping + customsDuty + excise + vat + repair
 *   customsDuty = (carPrice + shipping) * dutyRate
 *   excise      = (carPrice + shipping) * exciseRate
 *   vat         = (carPrice + shipping + customsDuty + excise) * vatRate
 * </pre>
 * Substituting and collecting terms in carPrice gives {@code carPrice * (1+C) + shipping * C + repair = budget}
 * where {@code C = dutyRate + exciseRate + (1 + dutyRate + exciseRate) * vatRate}, so
 * {@code carPrice = (budget - repair - shipping * C) / (1 + C)}.
 */
@Component
@RequiredArgsConstructor
public class MaxBidCalculator {

    private final ImportRatesConfig rates;
    private final CurrencyExchangeService exchangeService;

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

        BigDecimal dutyRate = rates.getCustomsDutyRate();
        BigDecimal vatRate = rates.getVatRate();
        BigDecimal exciseRate = ExciseRateResolver.resolve(input.getEngineCapacityCm3(), input.getFuelType());

        // C = d + e + (1+d+e)*v
        BigDecimal onePlusDutyPlusExcise = BigDecimal.ONE.add(dutyRate).add(exciseRate);
        BigDecimal combinedRate = dutyRate.add(exciseRate).add(onePlusDutyPlusExcise.multiply(vatRate));
        BigDecimal onePlusCombined = BigDecimal.ONE.add(combinedRate);

        BigDecimal numerator = input.getBudgetPln()
            .subtract(repair)
            .subtract(totalShippingPln.multiply(combinedRate));

        boolean sufficient = numerator.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal maxCarPricePln = sufficient
            ? numerator.divide(onePlusCombined, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal customsValue = maxCarPricePln.add(totalShippingPln);
        BigDecimal customsDuty = customsValue.multiply(dutyRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal excise = customsValue.multiply(exciseRate).setScale(2, RoundingMode.HALF_UP);
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
