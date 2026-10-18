package com.carmarket.auction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One W8 Shipping towing price: US auction yard (or sublot) → W8 warehouse/port, USD per vehicle.
 * Seeded by Flyway (V5, price list {@code W8-2025-26}) and never written by the application; a new W8 price
 * list is a new migration with a new {@link #priceList}. No row for a yard = W8 has no towing offer there.
 * {@link #yardKey} is {@code W8LocationKey.normalize(yard)}.
 */
@Entity
@Immutable
@Table(name = "w8_towing_rate")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class W8TowingRate {

    @Id
    private Long id;

    @Column(name = "price_list", nullable = false, length = 20)
    private String priceList;

    @Enumerated(EnumType.STRING)
    @Column(name = "auction", nullable = false, length = 10)
    private W8Auction auction;

    /** Display name, e.g. {@code CHICAGO SOUTH - IL (Heights Sublot)}. */
    @Column(name = "location", nullable = false, length = 120)
    private String location;

    @Column(name = "yard", nullable = false, length = 80)
    private String yard;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "sublot", length = 80)
    private String sublot;

    @Column(name = "yard_key", nullable = false, length = 80)
    private String yardKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "port", nullable = false, length = 12)
    private W8Port port;

    @Column(name = "price_usd", nullable = false, precision = 8, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
