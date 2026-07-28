package com.carmarket.auction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_calculations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private AuctionLot lot;

    @Embedded
    private ImportCostBreakdown breakdown;

    @Column(name = "target_sale_price_pln", precision = 12, scale = 2)
    private BigDecimal targetSalePricePln;

    @Column(name = "estimated_repair_cost_pln", precision = 12, scale = 2)
    private BigDecimal estimatedRepairCostPln;

    @Column(name = "estimated_profit_pln", precision = 12, scale = 2)
    private BigDecimal estimatedProfitPln;

    @Column(name = "profit_margin_percent", precision = 5, scale = 2)
    private BigDecimal profitMarginPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "profit_rating")
    private ProfitRating profitRating;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ProfitRating { A_EXCELLENT, B_GOOD, C_MARGINAL, D_RISKY }
}
