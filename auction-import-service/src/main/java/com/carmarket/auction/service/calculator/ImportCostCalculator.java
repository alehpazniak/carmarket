package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.ImportCalculation;
import com.carmarket.auction.entity.ImportCostBreakdown;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.service.CurrencyExchangeService;
import com.carmarket.auction.service.shipping.W8LocationKey;
import com.carmarket.auction.service.shipping.W8QuoteRequest;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportCostCalculator {

    private final ImportRatesConfig rates;
    private final CurrencyExchangeService exchangeService;
    private final CustomsDutyResolver dutyResolver;
    private final W8ShippingQuoteService w8QuoteService;

    public ImportCalculation calculate(AuctionLot lot, CalculationInput input) {
        BigDecimal auctionPrice = lot.getAuctionPrice() != null
            ? lot.getAuctionPrice()
            : lot.getBuyNowPrice();

        if (auctionPrice == null) {
            throw new IllegalArgumentException("Lot has no price: " + lot.getLotNumber());
        }

        // 1. Auction fee (tiered)
        BigDecimal auctionFee = calculateAuctionFee(auctionPrice, lot.getSource());

        // 2–4, 7, 8: W8 Shipping's prices when the lot's yard is in W8's table, else the flat import.rates
        W8ShippingQuote w8 = w8QuoteService.quote(new W8QuoteRequest(W8Auction.from(lot.getSource()).orElse(null),
            lot.getAuctionLocation(), input.getSuv(), input.getPolandZone(), input.getLengthClass())).orElse(null);
        BigDecimal usDelivery;          // 2. US towing to the port (USD)
        BigDecimal oceanFreight;        // 3. Ocean freight to the EU port (USD)
        BigDecimal euPortFee;           // 4. EU port: forwarder (USD)
        BigDecimal customsClearance;    // 7. Customs broker + scan (PLN)
        BigDecimal euDelivery;          // 8. Truck to Poland (PLN)
        BigDecimal w8FeesUsd;           // W8 access + dealer fee (USD)
        if (w8 != null) {
            usDelivery = w8.chosen().towingUsd();
            oceanFreight = w8.chosen().oceanFreightUsd();
            euPortFee = w8.forwarderEur().multiply(exchangeService.getEurToUsdRate()).setScale(2, RoundingMode.HALF_UP);
            customsClearance = w8.brokerEur().add(w8.scanEur()).multiply(w8.eurPlnRate()).setScale(2, RoundingMode.HALF_UP);
            euDelivery = w8.polandDeliveryPln();
            w8FeesUsd = w8.w8FeesUsd();
        } else {
            usDelivery = estimateUsDelivery(W8LocationKey.parse(lot.getAuctionLocation()).state());
            oceanFreight = rates.getOceanFreightUsd();
            euPortFee = rates.getEuPortFeeUsd();
            customsClearance = rates.getCustomsClearancePln();
            euDelivery = rates.getEuDeliveryPln();
            w8FeesUsd = BigDecimal.ZERO;
        }

        // Customs value = transport costs up to the EU border included (W8's calculator, EU customs rules)
        BigDecimal customsValue = customsValue(auctionPrice, auctionFee, usDelivery, oceanFreight);

        // 5. Customs duty ("cło") — by country of manufacture (VIN), see CustomsDutyResolver
        BigDecimal customsDuty = customsValue.multiply(dutyResolver.resolve(lot.getVin()))
            .setScale(2, RoundingMode.HALF_UP);

        // 5b. Excise (Poland) — depends on engine capacity & fuel type; for imports the base is the
        // customs value increased by the duty (Polish excise act)
        BigDecimal exciseRate = ExciseRateResolver.resolve(lot.getEngineCapacity(), lot.getFuelType());
        BigDecimal excise = customsValue.add(customsDuty).multiply(exciseRate)
            .setScale(2, RoundingMode.HALF_UP);

        // 6. VAT 23% (Poland) — base = customs value + duty + excise
        BigDecimal vatBase = customsValue.add(customsDuty).add(excise);
        BigDecimal vat = vatBase.multiply(rates.getVatRate())
            .setScale(2, RoundingMode.HALF_UP);

        // Totals in USD
        BigDecimal totalUsd = auctionPrice
            .add(auctionFee)
            .add(usDelivery)
            .add(oceanFreight)
            .add(euPortFee)
            .add(w8FeesUsd);

        // Convert to PLN
        BigDecimal exchangeRate = exchangeService.getUsdToPlnRate();
        BigDecimal totalPln = totalUsd.multiply(exchangeRate)
            .add(excise.multiply(exchangeRate)) // excise usually paid in PLN
            .add(customsDuty.multiply(exchangeRate)) // customs duty usually paid in PLN
            .add(vat.multiply(exchangeRate))
            .add(customsClearance)
            .add(euDelivery)
            .setScale(2, RoundingMode.HALF_UP);

        ImportCostBreakdown breakdown = ImportCostBreakdown.builder()
            .auctionPrice(auctionPrice)
            .auctionFee(auctionFee)
            .usDelivery(usDelivery)
            .oceanFreight(oceanFreight)
            .euPortFee(euPortFee)
            .excise(excise.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP))
            .customsDuty(customsDuty.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP))
            .vat(vat.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP))
            .customsClearance(customsClearance)
            .euDelivery(euDelivery)
            .totalUsd(totalUsd)
            .totalPln(totalPln)
            .exchangeRate(exchangeRate)
            .exciseRate(exciseRate)
            .build();

        // Profit analysis
        BigDecimal repairCost = input.getEstimatedRepairCostPln() != null
            ? input.getEstimatedRepairCostPln()
            : BigDecimal.ZERO;

        BigDecimal targetSale = input.getTargetSalePricePln();
        BigDecimal totalCost = totalPln.add(repairCost);

        BigDecimal profit = targetSale != null
            ? targetSale.subtract(totalCost)
            : BigDecimal.ZERO;

        BigDecimal marginPercent = targetSale != null && targetSale.compareTo(BigDecimal.ZERO) > 0
            ? profit.divide(targetSale, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        ImportCalculation.ProfitRating rating = rateProfit(marginPercent, profit);

        return ImportCalculation.builder()
            .lot(lot)
            .breakdown(breakdown)
            .targetSalePricePln(targetSale)
            .estimatedRepairCostPln(repairCost)
            .estimatedProfitPln(profit)
            .profitMarginPercent(marginPercent)
            .profitRating(rating)
            .shippingQuote(w8)
            .build();
    }

    /** Auction price + auction fee + US towing + ocean freight: every cost up to the EU border. */
    static BigDecimal customsValue(BigDecimal auctionPrice, BigDecimal auctionFee, BigDecimal usDelivery, BigDecimal oceanFreight) {
        return auctionPrice.add(auctionFee).add(usDelivery).add(oceanFreight);
    }

    private BigDecimal calculateAuctionFee(BigDecimal price, AuctionLot.AuctionSource source) {
        // Copart & IAAI have different fee structures
        // Simplified tiered approach:
        if (price.compareTo(new BigDecimal("5000")) <= 0) {
            return price.multiply(new BigDecimal("0.10")); // 10%
        } else if (price.compareTo(new BigDecimal("10000")) <= 0) {
            return new BigDecimal("500").add(price.subtract(new BigDecimal("5000"))
                .multiply(new BigDecimal("0.05")));
        } else {
            return new BigDecimal("750").add(price.subtract(new BigDecimal("10000"))
                .multiply(new BigDecimal("0.025")));
        }
    }

    private BigDecimal estimateUsDelivery(String state) {
        // Map state to zone: East Coast, West Coast, Midwest, South
        String zone = resolveStateZone(state);
        return switch (zone) {
            case "EAST" -> new BigDecimal("350");
            case "WEST" -> new BigDecimal("550");
            case "MIDWEST" -> new BigDecimal("450");
            case "SOUTH" -> new BigDecimal("400");
            default -> new BigDecimal("400");
        };
    }

    private String resolveStateZone(String state) {
        if (state == null) return "EAST";
        return switch (state.toUpperCase()) {
            case "CA", "OR", "WA", "NV", "AZ" -> "WEST";
            case "TX", "FL", "GA", "NC", "SC", "AL", "MS", "LA", "TN" -> "SOUTH";
            case "IL", "OH", "MI", "IN", "WI", "MN", "MO", "IA" -> "MIDWEST";
            default -> "EAST";
        };
    }

    private ImportCalculation.ProfitRating rateProfit(BigDecimal margin, BigDecimal absoluteProfit) {
        if (margin.compareTo(new BigDecimal("25")) >= 0 && absoluteProfit.compareTo(new BigDecimal("10000")) >= 0) {
            return ImportCalculation.ProfitRating.A_EXCELLENT;
        } else if (margin.compareTo(new BigDecimal("15")) >= 0) {
            return ImportCalculation.ProfitRating.B_GOOD;
        } else if (margin.compareTo(new BigDecimal("5")) >= 0) {
            return ImportCalculation.ProfitRating.C_MARGINAL;
        } else {
            return ImportCalculation.ProfitRating.D_RISKY;
        }
    }
}
