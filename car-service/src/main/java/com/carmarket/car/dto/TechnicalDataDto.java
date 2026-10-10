package com.carmarket.car.dto;

import com.carmarket.car.entity.BodyType;
import com.carmarket.car.entity.DriveType;
import com.carmarket.car.entity.VehicleCondition;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalDataDto {

    @Size(max = 100)
    private String version;

    @Min(0)
    @Max(20000)
    private Integer engineCapacity;

    @Min(0)
    @Max(3000)
    private Integer powerHp;

    private DriveType driveType;

    private BodyType bodyType;

    @Min(1)
    @Max(10)
    private Integer doors;

    @Min(1)
    @Max(100)
    private Integer seats;

    @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$", message = "VIN must be 17 characters (no I, O, Q)")
    private String vin;

    private VehicleCondition condition;

    @Size(max = 100)
    private String originCountry;

    @PastOrPresent
    private LocalDate firstRegistrationDate;

    private Boolean registeredInPoland;

    private Boolean firstOwner;

    private Boolean accidentFree;

    private Boolean asoServiced;

    private Boolean damaged;
}
