package com.carmarket.user.mapper;

import com.carmarket.user.dto.UserProfileRequest;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import org.mapstruct.Mapper;

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
    UserProfile toEntity(UserProfileRequest request);

}
