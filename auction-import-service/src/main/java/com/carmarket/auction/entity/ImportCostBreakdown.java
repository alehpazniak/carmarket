package com.carmarket.auction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportCostBreakdown {

    @Column(name = "auction_price", precision = 12, scale = 2)
    private BigDecimal auctionPrice;

    @Column(name = "auction_fee", precision = 12, scale = 2)
    private BigDecimal auctionFee;

    @Column(name = "us_delivery", precision = 12, scale = 2)
    private BigDecimal usDelivery;

    @Column(name = "ocean_freight", precision = 12, scale = 2)
    private BigDecimal oceanFreight;

    @Column(name = "eu_port_fee", precision = 12, scale = 2)
    private BigDecimal euPortFee;

    @Column(name = "excise", precision = 12, scale = 2)
    private BigDecimal excise;

    @Column(name = "vat", precision = 12, scale = 2)
    private BigDecimal vat;

    @Column(name = "customs_clearance", precision = 12, scale = 2)
    private BigDecimal customsClearance;

    @Column(name = "eu_delivery", precision = 12, scale = 2)
    private BigDecimal euDelivery;

    @Column(name = "total_usd", precision = 12, scale = 2)
    private BigDecimal totalUsd;

    @Column(name = "total_pln", precision = 12, scale = 2)
    private BigDecimal totalPln;

    @Column(name = "exchange_rate", precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @Column(name = "excise_rate", precision = 5, scale = 2)
    private BigDecimal exciseRate;

    public BigDecimal getTotalFeesUsd() {
        return auctionFee.add(usDelivery).add(oceanFreight).add(euPortFee);
    }
}
