package com.carmarket.user.dto;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.entity.UserProfile.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Another user's profile as anyone may see it (GET /users/{id}).
 * <p>
 * An explicit allow-list built by hand, not by MapStruct: a field added to {@link UserProfile}
 * never shows up here by accident. Email, phone, street address and account state stay private;
 * the phone is only available per listing via GET /users/{id}/phone.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicUserProfileResponse(
    UUID id,
    String displayName,
    String avatarUrl,
    String city,
    String bio,
    UserRole role,
    LocalDateTime memberSince
) {
    public static PublicUserProfileResponse from(UserProfile p) {
        return new PublicUserProfileResponse(
            p.getId(), p.getDisplayName(), p.getAvatarUrl(), p.getCity(), p.getBio(), p.getRole(), p.getCreatedAt());
    }
}
