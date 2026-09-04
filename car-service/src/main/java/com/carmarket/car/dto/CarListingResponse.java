package com.carmarket.car.dto;

import com.carmarket.car.entity.FuelType;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.entity.Transmission;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarListingResponse {
    private UUID id;
    private UUID sellerId;
    private String make;
    private String model;
    private Integer year;
    private BigDecimal price;
    private Integer mileage;
    private FuelType fuelType;
    private Transmission transmission;
    private String color;
    private String description;
    private String city;
    private String country;
    private List<String> imageUrls;
    private String primaryImageUrl;
    private ListingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
