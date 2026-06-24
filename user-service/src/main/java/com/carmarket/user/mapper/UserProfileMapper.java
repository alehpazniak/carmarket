package com.carmarket.user.mapper;

import com.carmarket.user.dto.UserProfileRequest;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for UserProfile entity ↔ DTO conversions.
 */
@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    /**
     * Convert UserProfile entity to response DTO.
     */
    UserProfileResponse toResponse(UserProfile entity);

    /**
     * Convert request DTO to UserProfile entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "listingsCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserProfile toEntity(UserProfileRequest request);

}
