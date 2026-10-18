package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.service.CurrencyExchangeService;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Solves for the maximum price a buyer can bid on a US auction lot given a fixed total budget.
 * <p>
 * Shipping comes from W8 Shipping's quote when the yard is in W8's table ({@link MaxBidInput#getW8Quote()}):
 * towing + ocean freight up to the EU border are inside the customs value, while everything after the
 * border (Rotterdam forwarder, broker, scan, truck to Poland) and W8's fees are not. Without a quote it's
 * two line items, both inside the customs value: the Apibara auction-to-port quote plus a flat
 * shipping-delivery cost for the leg Apibara doesn't price (US port → Poland), $1500 for SUVs, $1000 otherwise.
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
 * {@code carPrice = (budget - repair) / M - shipping}. With a W8 quote, shipping = toEuBorder and the costs
 * outside the customs value come off the budget first:
 * {@code carPrice = (budget - repair - outside) / M - toEuBorder}. The duty rate depends on the country of manufacture
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
        BigDecimal exchangeRate = exchangeService.getUsdToPlnRate();
        W8ShippingQuote w8 = input.getW8Quote();

        BigDecimal shippingUsd;          // inside the customs value
        BigDecimal shippingPln;
        BigDecimal shippingDeliveryUsd;  // shown as the second shipping line
        BigDecimal shippingDeliveryPln;
        BigDecimal insideCustomsPln;     // shipping costs that are part of the customs value
        BigDecimal outsideCustomsPln;    // costs after the EU border, not part of it
        if (w8 != null) {
            shippingUsd = w8.toEuBorderUsd();
            shippingPln = shippingUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            shippingDeliveryPln = w8.afterBorderPln()
                .add(w8.w8FeesUsd().multiply(exchangeRate)).setScale(2, RoundingMode.HALF_UP);
            shippingDeliveryUsd = shippingDeliveryPln.divide(exchangeRate, 2, RoundingMode.HALF_UP);
            insideCustomsPln = shippingPln;
            outsideCustomsPln = shippingDeliveryPln;
        } else {
            shippingUsd = input.getShippingCostUsd() != null
                ? input.getShippingCostUsd()
                : rates.getOceanFreightUsd();
            shippingDeliveryUsd = Boolean.TRUE.equals(input.getSuv())
                ? rates.getShippingDeliverySuvUsd()
                : rates.getShippingDeliveryUsd();
            shippingPln = shippingUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            shippingDeliveryPln = shippingDeliveryUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            insideCustomsPln = shippingPln.add(shippingDeliveryPln);
            outsideCustomsPln = BigDecimal.ZERO;
        }

        BigDecimal dutyRate = dutyResolver.resolve(input.getVin());
        BigDecimal vatRate = rates.getVatRate();
        BigDecimal exciseRate = ExciseRateResolver.resolve(input.getEngineCapacityCm3(), input.getFuelType());

        // M = (1+d)(1+e)(1+v): customs value → customs value + duty + excise + VAT
        BigDecimal multiplier = BigDecimal.ONE.add(dutyRate)
            .multiply(BigDecimal.ONE.add(exciseRate))
            .multiply(BigDecimal.ONE.add(vatRate));

        BigDecimal maxCustomsValue = input.getBudgetPln().subtract(repair).subtract(outsideCustomsPln)
            .divide(multiplier, 10, RoundingMode.HALF_UP);
        BigDecimal carPrice = maxCustomsValue.subtract(insideCustomsPln);

        boolean sufficient = carPrice.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal maxCarPricePln = sufficient
            ? carPrice.setScale(2, RoundingMode.DOWN)
            : BigDecimal.ZERO;

        BigDecimal customsValue = maxCarPricePln.add(insideCustomsPln);
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
            .w8Quote(w8)
            .build();
    }
}
