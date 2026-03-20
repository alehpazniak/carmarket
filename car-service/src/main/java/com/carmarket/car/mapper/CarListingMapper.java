package com.carmarket.car.mapper;

import com.carmarket.car.dto.CarListingRequest;
import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.entity.CarListing;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CarListingMapper {

    CarListing toEntity(CarListingRequest request);

    CarListingResponse toResponse(CarListing entity);

    /**
     * Used for PATCH — only non-null fields are updated
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CarListingRequest request, @MappingTarget CarListing entity);
}
