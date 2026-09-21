package com.carmarket.car.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CarUpdatedEvent(String carId,
                              String sellerId,
                              String make,
                              String model,
                              Integer year,
                              BigDecimal price,
                              Integer mileage,
                              String fuelType,
                              String transmission,
                              String city,
                              String status,
                              String primaryImageUrl,
                              LocalDateTime createdAt

) {
}
