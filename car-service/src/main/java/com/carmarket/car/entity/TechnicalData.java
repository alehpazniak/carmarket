package com.carmarket.car.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.LocalDate;

/**
 * Otomoto-style "Dane techniczne" block of a listing. All fields are optional.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalData {

    @Column(length = 100)
    private String version;

    @Column(name = "engine_capacity")
    private Integer engineCapacity;

    @Column(name = "power_hp")
    private Integer powerHp;

    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type", length = 20)
    private DriveType driveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", length = 20)
    private BodyType bodyType;

    private Integer doors;

    private Integer seats;

    @Column(length = 17)
    private String vin;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_condition", length = 10)
    private VehicleCondition condition;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(name = "first_registration_date")
    private LocalDate firstRegistrationDate;

    @Column(name = "registered_in_poland")
    private Boolean registeredInPoland;

    @Column(name = "first_owner")
    private Boolean firstOwner;

    @Column(name = "accident_free")
    private Boolean accidentFree;

    @Column(name = "aso_serviced")
    private Boolean asoServiced;

    private Boolean damaged;
}
