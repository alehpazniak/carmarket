package com.carmarket.car.dto;

import com.carmarket.car.entity.ListingStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Just enough of a listing to label it elsewhere (e.g. a chat conversation), including REMOVED ones,
 * so a deleted listing still shows its title instead of disappearing.
 */
public record CarListingSummary(
    UUID id,
    String make,
    String model,
    Integer year,
    BigDecimal price,
    String primaryImageUrl,
    ListingStatus status
) {
}
