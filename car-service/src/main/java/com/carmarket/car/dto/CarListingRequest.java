package com.carmarket.car.dto;

import com.carmarket.car.entity.CarListing.FuelType;
import com.carmarket.car.entity.CarListing.Transmission;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarListingRequest {

    @NotBlank(message = "Make is required")
    @Size(max = 100)
    private String make;

    @NotBlank(message = "Model is required")
    @Size(max = 100)
    private String model;

    @NotNull
    @Min(1886)
    @Max(2100)
    private Integer year;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @Min(0)
    private Integer mileage;

    private FuelType fuelType;

    private Transmission transmission;

    @Size(max = 50)
    private String color;

    @Size(max = 2000)
    private String description;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String country;
}
