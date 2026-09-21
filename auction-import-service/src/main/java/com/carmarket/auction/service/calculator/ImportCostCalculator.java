package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.ImportCalculation;
import com.carmarket.auction.entity.ImportCostBreakdown;
import com.carmarket.auction.service.CurrencyExchangeService;
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

    public ImportCalculation calculate(AuctionLot lot, CalculationInput input) {
        BigDecimal auctionPrice = lot.getAuctionPrice() != null
            ? lot.getAuctionPrice()
            : lot.getBuyNowPrice();

        if (auctionPrice == null) {
            throw new IllegalArgumentException("Lot has no price: " + lot.getLotNumber());
        }

        // 1. Auction fee (tiered)
        BigDecimal auctionFee = calculateAuctionFee(auctionPrice, lot.getSource());

        // 2. US delivery (by state/zone)
        BigDecimal usDelivery = estimateUsDelivery(lot.getAuctionLocation());

        // 3. Ocean freight (fixed or by vehicle type)
        BigDecimal oceanFreight = rates.getOceanFreightUsd();

        // 4. EU port fee
        BigDecimal euPortFee = rates.getEuPortFeeUsd();

        // 5. Excise (Poland) — depends on engine capacity & fuel type
        BigDecimal exciseRate = ExciseRateResolver.resolve(lot.getEngineCapacity(), lot.getFuelType());
        // Customs value = auction price + ocean freight (Polish customs rules)
        BigDecimal customsValue = auctionPrice.add(oceanFreight);
        BigDecimal excise = customsValue.multiply(exciseRate)
            .setScale(2, RoundingMode.HALF_UP);

        // 5b. Customs duty ("cło") — non-EU common external tariff on the customs value
        BigDecimal customsDuty = customsValue.multiply(rates.getCustomsDutyRate())
            .setScale(2, RoundingMode.HALF_UP);

        // 6. VAT 23% (Poland) — base = customs value + duty + excise
        BigDecimal vatBase = customsValue.add(customsDuty).add(excise);
        BigDecimal vat = vatBase.multiply(rates.getVatRate())
            .setScale(2, RoundingMode.HALF_UP);

        // 7. Customs clearance
        BigDecimal customsClearance = rates.getCustomsClearancePln();

        // 8. EU delivery to Poland
        BigDecimal euDelivery = rates.getEuDeliveryPln();

        // Totals in USD
        BigDecimal totalUsd = auctionPrice
            .add(auctionFee)
            .add(usDelivery)
            .add(oceanFreight)
            .add(euPortFee);

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
            .build();
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

    private BigDecimal estimateUsDelivery(String auctionLocation) {
        // Map state to zone: East Coast, West Coast, Midwest, South
        String zone = resolveStateZone(auctionLocation);
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
