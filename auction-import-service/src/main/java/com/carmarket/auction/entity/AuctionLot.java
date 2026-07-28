package com.carmarket.auction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auction_lots", indexes = {
    @Index(name = "idx_auction_lots_vin", columnList = "vin"),
    @Index(name = "idx_auction_lots_source", columnList = "source"),
    @Index(name = "idx_auction_lots_status", columnList = "status"),
    @Index(name = "idx_auction_lots_make_model", columnList = "make, model, year")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vin", length = 17, nullable = false)
    private String vin;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionSource source;

    @Column(name = "lot_number", nullable = false, unique = true)
    private String lotNumber;

    @Column(name = "auction_price", precision = 12, scale = 2)
    private BigDecimal auctionPrice;

    @Column(name = "buy_now_price", precision = 12, scale = 2)
    private BigDecimal buyNowPrice;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "damage_type")
    private String damageType;

    @Column(name = "primary_damage")
    private String primaryDamage;

    @Column(name = "secondary_damage")
    private String secondaryDamage;

    private Integer odometer;

    @Column(name = "odometer_unit", length = 10)
    @Builder.Default
    private String odometerUnit = "mi";

    @Column(name = "engine_capacity")
    private Integer engineCapacity; // cm3

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type")
    private FuelType fuelType;

    @Column(name = "transmission")
    private String transmission;

    @Column(name = "auction_location")
    private String auctionLocation; // State, e.g. "CA", "TX"

    @Column(name = "auction_date")
    private LocalDateTime auctionDate;

    @Column(name = "sale_date")
    private LocalDateTime saleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LotStatus status = LotStatus.LIVE;

    @Column(name = "image_urls")
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "auction_lot_images", joinColumns = @JoinColumn(name = "lot_id"))
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData; // JSON snapshot from parser

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isSold() {
        return this.status == LotStatus.SOLD;
    }

    public boolean hasBuyNow() {
        return this.buyNowPrice != null && this.buyNowPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public enum AuctionSource { COPART, IAAI }

    public enum LotStatus { LIVE, SOLD, UNSOLD, EXPIRED, REMOVED }

    public enum FuelType { PETROL, DIESEL, ELECTRIC, HYBRID, PLUGIN_HYBRID }
}
