package com.carmarket.auction.analytics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "analytics_vehicle_stats", indexes = {
    @Index(name = "idx_stats_lookup", columnList = "make, model, year, damageType", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    private Integer year;

    @Column(name = "damage_type")
    private String damageType;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "avg_purchase_price", precision = 12, scale = 2)
    private BigDecimal avgPurchasePrice;

    @Column(name = "median_purchase_price", precision = 12, scale = 2)
    private BigDecimal medianPurchasePrice;

    @Column(name = "avg_repair_cost", precision = 12, scale = 2)
    private BigDecimal avgRepairCost;

    @Column(name = "avg_sale_price_pln", precision = 12, scale = 2)
    private BigDecimal avgSalePricePln;

    @Column(name = "avg_profit", precision = 12, scale = 2)
    private BigDecimal avgProfit;

    @Column(name = "median_profit", precision = 12, scale = 2)
    private BigDecimal medianProfit;

    @Column(name = "avg_days_to_sell")
    private Integer avgDaysToSell;

    @Column(name = "loss_rate_percent", precision = 5, scale = 2)
    private BigDecimal lossRatePercent;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
