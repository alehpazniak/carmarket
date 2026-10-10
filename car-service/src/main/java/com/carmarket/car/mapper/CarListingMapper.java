package com.carmarket.car.mapper;

import com.carmarket.car.dto.CarListingRequest;
import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.dto.TechnicalDataDto;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.TechnicalData;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CarListingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "imageUrls", ignore = true)
    @Mapping(target = "primaryImageUrl", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "videoUrl", ignore = true)
    @Mapping(target = "promotedUntil", ignore = true)
    @Mapping(target = "highlightedUntil", ignore = true)
    CarListing toEntity(CarListingRequest request);

    CarListingResponse toResponse(CarListing entity);

    /**
     * Used for PATCH — only non-null fields are updated
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "imageUrls", ignore = true)
    @Mapping(target = "primaryImageUrl", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "videoUrl", ignore = true)
    @Mapping(target = "technicalData", ignore = true)
    @Mapping(target = "promotedUntil", ignore = true)
    @Mapping(target = "highlightedUntil", ignore = true)
    void updateEntity(CarListingRequest request, @MappingTarget CarListing entity);

    TechnicalData toTechnicalData(TechnicalDataDto dto);
}
