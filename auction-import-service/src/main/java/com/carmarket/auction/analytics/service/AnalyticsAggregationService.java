package com.carmarket.auction.analytics.service;

import com.carmarket.auction.analytics.entity.VehicleStats;
import com.carmarket.auction.analytics.repository.VehicleStatsRepository;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.ImportCalculation;
import com.carmarket.auction.repository.AuctionLotRepository;
import com.carmarket.auction.repository.ImportCalculationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsAggregationService {

    private final AuctionLotRepository lotRepository;
    private final ImportCalculationRepository calculationRepository;
    private final VehicleStatsRepository statsRepository;

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    @Transactional
    public void rebuildStats() {
        log.info("Starting analytics aggregation...");

        // Get all sold lots with calculations
        List<ImportCalculation> calculations = calculationRepository.findAllWithSoldLots();

        // Group by make, model, year, damageType
        Map<String, List<ImportCalculation>> grouped = calculations.stream()
            .collect(Collectors.groupingBy(c -> key(c)));

        for (Map.Entry<String, List<ImportCalculation>> entry : grouped.entrySet()) {
            List<ImportCalculation> group = entry.getValue();
            if (group.size() < 3) continue; // Need minimum sample size

            VehicleStats stats = computeStats(group);

            statsRepository.findByMakeAndModelAndYearAndDamageType(
                    stats.getMake(), stats.getModel(), stats.getYear(), stats.getDamageType())
                .ifPresent(existing -> stats.setId(existing.getId()));

            statsRepository.save(stats);
        }

        log.info("Analytics aggregation complete. Processed {} groups.", grouped.size());
    }

    private VehicleStats computeStats(List<ImportCalculation> group) {
        AuctionLot firstLot = group.get(0).getLot();

        List<BigDecimal> profits = group.stream()
            .map(ImportCalculation::getEstimatedProfitPln)
            .sorted()
            .toList();

        BigDecimal avgProfit = profits.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(profits.size()), 2, RoundingMode.HALF_UP);

        BigDecimal medianProfit = profits.size() % 2 == 0
            ? profits.get(profits.size() / 2 - 1).add(profits.get(profits.size() / 2))
            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
            : profits.get(profits.size() / 2);

        long lossCount = profits.stream().filter(p -> p.compareTo(BigDecimal.ZERO) < 0).count();
        BigDecimal lossRate = BigDecimal.valueOf(lossCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(profits.size()), 2, RoundingMode.HALF_UP);

        List<BigDecimal> prices = group.stream()
            .map(c -> c.getBreakdown().getAuctionPrice())
            .sorted()
            .toList();

        BigDecimal medianPrice = prices.size() % 2 == 0
            ? prices.get(prices.size() / 2 - 1).add(prices.get(prices.size() / 2))
            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
            : prices.get(prices.size() / 2);

        return VehicleStats.builder()
            .make(firstLot.getMake())
            .model(firstLot.getModel())
            .year(firstLot.getYear())
            .damageType(firstLot.getDamageType())
            .sampleSize(group.size())
            .avgPurchasePrice(prices.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP))
            .medianPurchasePrice(medianPrice)
            .avgProfit(avgProfit)
            .medianProfit(medianProfit)
            .lossRatePercent(lossRate)
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    private String key(ImportCalculation c) {
        AuctionLot l = c.getLot();
        return String.join("|", l.getMake(), l.getModel(),
            String.valueOf(l.getYear()), l.getDamageType());
    }
}
