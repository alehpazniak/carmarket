package com.carmarket.auction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cached Apibara auction-to-port shipping quote for one US auction location (e.g. "Sacramento
 * (CA)"). The domestic trucking price only depends on the auction facility, not the individual
 * vehicle, so it's reused across every lot at that location instead of spending an Apibara call
 * (free tier: 100/month) per lookup. {@link #updatedAt} is checked against a max age (currently
 * one week) by {@code UsaShippingRateService} to decide whether to refresh via the API.
 */
@Entity
@Table(name = "usa_shipping_to_port")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsaShippingToPort {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "recommended_port")
    private String recommendedPort;

    @Column(name = "price_usd", precision = 12, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "has_shipping_price")
    private Boolean hasShippingPrice;

    /** Raw JSON of Apibara's {@code available_ports} list ({@code [{port, price}, ...]}). */
    @Column(name = "available_ports")
    private String availablePorts;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
