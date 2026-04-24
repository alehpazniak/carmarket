package com.carmarket.user.dto;

import com.carmarket.user.entity.UserProfile.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for returning user profile data.
 * Only includes non-sensitive fields.
 * Uses @JsonInclude to exclude null values from JSON response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private UUID id;
    private String email;
    private String displayName;
    private String phoneNumber;
    private String city;
    private String country;
    private String bio;
    private String avatarUrl;
    private UserRole role;
    private Boolean active;
    private Integer listingsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
