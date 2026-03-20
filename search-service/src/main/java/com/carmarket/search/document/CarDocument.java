package com.carmarket.search.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Elasticsearch document for car listings.
 *
 * Index: car_listings
 * Each field is typed for optimal search:
 *   - make/model: both keyword (exact) + text (full-text) via multi-field
 *   - price/mileage/year: integer/double for range queries
 *   - city: keyword for aggregations
 *   - description: analyzed text for full-text search
 */
@Document(indexName = "car_listings")
@Setting(settingPath = "elasticsearch/settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarDocument {

    @Id
    private String id;           // carId from car-service

    @Field(type = FieldType.Keyword)
    private String sellerId;

    @Field(type = FieldType.Text, analyzer = "standard",
        fielddata = true)
    private String make;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String model;

    @Field(type = FieldType.Integer)
    private Integer year;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer mileage;

    @Field(type = FieldType.Keyword)
    private String fuelType;

    @Field(type = FieldType.Keyword)
    private String transmission;

    @Field(type = FieldType.Keyword)
    private String color;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Keyword)
    private String country;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = {DateFormat.date_optional_time, DateFormat.epoch_millis})
    private Instant createdAt;
}
