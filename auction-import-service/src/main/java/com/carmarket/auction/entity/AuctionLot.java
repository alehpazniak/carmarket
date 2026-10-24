package com.carmarket.auction.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auction_lots", uniqueConstraints = {
    // Copart and IAAI lot numbers can collide, so a lot is identified by both (V6)
    @UniqueConstraint(name = "uk_auction_lots_source_lot_number", columnNames = {"source", "lot_number"})
}, indexes = {
    @Index(name = "idx_auction_lots_vin", columnList = "vin"),
    @Index(name = "idx_auction_lots_source", columnList = "source"),
    @Index(name = "idx_auction_lots_status", columnList = "status"),
    @Index(name = "idx_auction_lots_make_model", columnList = "make, model, year"),
    @Index(name = "idx_auction_lots_vehicle_type", columnList = "vehicle_type")
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

    @Column(name = "lot_number", nullable = false)
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
    private Instant auctionDate; // TIMESTAMPTZ (V6); the frontend shows it in Europe/Warsaw

    @Column(name = "sale_date")
    private LocalDateTime saleDate; // UTC; set for SOLD lots from the feed

    /** Apibara's Vehicle.type as sent, e.g. "AUTOMOBILE", "PICKUP" (V8) */
    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    /** Apibara's vehicle_specs.body_style as sent (free text: Copart Body Style / IAAI VehicleClass) */
    @Column(name = "body_style", length = 50)
    private String bodyStyle;

    /** Final price of a SOLD lot (Apibara sale_price_usd) */
    @Column(name = "sold_price", precision = 12, scale = 2)
    private BigDecimal soldPrice;

    /** When the auction ended, for SOLD / UNSOLD / ended-without-result lots */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** The last sync that returned this lot, changed or not; internal */
    @JsonIgnore
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LotStatus status = LotStatus.LIVE;

    @Column(name = "image_urls")
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "auction_lot_images", joinColumns = @JoinColumn(name = "lot_id"))
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();

    @JsonIgnore
    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData; // the lot's JSON as the API returned it; internal, never in responses

    /** LotContentHash of the mapped fields; a sync rewrites the lot only when it differs. */
    @JsonIgnore
    @Column(name = "content_hash", length = 64)
    private String contentHash;

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
