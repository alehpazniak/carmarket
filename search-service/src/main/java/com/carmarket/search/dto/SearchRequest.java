package com.carmarket.search.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * All search parameters are optional — build a dynamic query from whatever is provided.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SearchRequest {

    private String query;          // full-text: searches make, model, description

    private String make;
    private String model;
    private Integer yearFrom;
    private Integer yearTo;

    private BigDecimal priceFrom;
    private BigDecimal priceTo;

    private Integer mileageMax;

    private String fuelType;
    private String transmission;
    private String city;
    private String country;
}
