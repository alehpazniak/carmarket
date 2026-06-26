package com.carmarket.user.controller;

import com.carmarket.user.dto.UserProfileRequest;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.mapper.UserProfileMapper;
import com.carmarket.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for user profile management.
 * <p>
 * Endpoints:
 * - GET  /api/users/me             — Get current user profile (requires auth)
 * - GET  /api/users/{id}           — Get user public profile
 * - PATCH /api/users/me            — Update own profile (requires auth)
 * - DELETE /api/users/me           — Deactivate own account (requires auth)
 * <p>
 * Security: X-User-Id header (injected by API Gateway JWT filter)
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final UserProfileMapper userProfileMapper;

    /**
     * Get current user's profile.
     * User ID comes from X-User-Id header (set by API Gateway).
     *
     * @param userId User ID from JWT token (via gateway)
     * @return User profile or 401 if not found
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Id") String userId) {
        log.info("Fetching profile for user: {}", userId);

        return userProfileService.getUserProfile(UUID.fromString(userId))
            .map(profile -> {
                UserProfileResponse response = userProfileMapper.toResponse(profile);
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> {
                log.warn("User not found: {}", userId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            });
    }

    /**
     * Get any user's public profile.
     * Public endpoint — no authentication required.
     *
     * @param id User ID to fetch
     * @return User profile or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID id) {
        log.info("Fetching public profile for user: {}", id);

        return userProfileService.getUserProfile(id)
            .map(profile -> {
                UserProfileResponse response = userProfileMapper.toResponse(profile);
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> {
                log.warn("User not found: {}", id);
                return ResponseEntity.notFound().build();
            });
    }

    /**
     * Update own profile (partial update).
     * Only provided fields are updated; null fields are ignored.
     *
     * @param userId  User ID from JWT (via gateway)
     * @param request Profile update request with validation
     * @return Updated profile or 404 if not found
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@RequestHeader("X-User-Id") String userId,
                                                             @Valid @RequestBody UserProfileRequest request) {
        UUID id = UUID.fromString(userId);
        log.info("Updating profile for user: {}", id);

        try {
            UserProfile updateEntity = userProfileMapper.toEntity(request);
            UserProfile updated = userProfileService.updateProfile(id, updateEntity);
            UserProfileResponse response = userProfileMapper.toResponse(updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update user {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete/Deactivate own account (soft delete).
     * Sets active = false instead of hard delete.
     *
     * @param userId User ID from JWT (via gateway)
     * @return 204 No Content on success
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteProfile(@RequestHeader("X-User-Id") String userId) {
        UUID id = UUID.fromString(userId);
        log.info("Deactivating account for user: {}", id);

        try {
            userProfileService.deactivateProfile(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to deactivate user {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Increment user's listings count.
     * Called by car-service when user creates a new listing.
     * Internal endpoint (not exposed via gateway).
     *
     * @param userId User ID
     * @return Updated profile
     */
    @PostMapping("/{userId}/increment-listings")
    public ResponseEntity<UserProfileResponse> incrementListings(@PathVariable UUID userId) {
        log.info("Incrementing listings count for user: {}", userId);

        try {
            userProfileService.incrementListingsCount(userId);
            return userProfileService.getUserProfile(userId)
                .map(profile -> {
                    UserProfileResponse response = userProfileMapper.toResponse(profile);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to increment listings for user {}: {}", userId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Decrement user's listings count.
     * Called by car-service when user deletes a listing.
     * Internal endpoint (not exposed via gateway).
     *
     * @param userId User ID
     * @return Updated profile
     */
    @PostMapping("/{userId}/decrement-listings")
    public ResponseEntity<UserProfileResponse> decrementListings(@PathVariable UUID userId) {
        log.info("Decrementing listings count for user: {}", userId);

        try {
            userProfileService.decrementListingsCount(userId);
            return userProfileService.getUserProfile(userId)
                .map(profile -> {
                    UserProfileResponse response = userProfileMapper.toResponse(profile);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decrement listings for user {}: {}", userId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
