package com.carmarket.user.controller;

import com.carmarket.user.dto.ContactInfoRequest;
import com.carmarket.user.dto.PhoneNumberResponse;
import com.carmarket.user.dto.PublicUserProfileResponse;
import com.carmarket.user.dto.UserProfileRequest;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.mapper.UserProfileMapper;
import com.carmarket.user.service.SellerPhoneService;
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
 * - GET  /api/users/{id}           — Get user public profile (allow-listed fields only)
 * - GET  /api/users/{id}/phone?carId= — Seller's phone for one of their ACTIVE listings (requires auth)
 * - PATCH /api/users/me            — Update own profile (requires auth)
 * - PUT  /api/users/me/contact     — Replace own phone and address (requires auth)
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
    private final SellerPhoneService sellerPhoneService;

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
     * Public endpoint — no authentication required, so it returns only the allow-listed
     * {@link PublicUserProfileResponse} fields (no email, phone, address or account state).
     *
     * @param id User ID to fetch
     * @return Public profile, or 404 if not found or deactivated
     */
    @GetMapping("/{id}")
    public ResponseEntity<PublicUserProfileResponse> getProfile(@PathVariable UUID id) {
        log.info("Fetching public profile for user: {}", id);

        return userProfileService.getUserProfile(id)
            .filter(UserProfile::isActive)
            .map(profile -> ResponseEntity.ok(PublicUserProfileResponse.from(profile)))
            .orElseGet(() -> {
                log.warn("User not found or inactive: {}", id);
                return ResponseEntity.notFound().build();
            });
    }

    /**
     * A seller's phone number, only for one of their ACTIVE listings. Requires a valid gateway
     * signature (see SecurityConfig) and is rate-limited per user at the gateway.
     * <p>
     * Every failed check answers the same bare 404, so the response doesn't reveal whether the
     * listing, its owner or the phone number was the problem. 503 if car-service can't be asked.
     *
     * @param requesterId Caller's user ID (via gateway), for the audit log
     * @param id          Seller's user ID
     * @param carId       Listing the phone number is requested for
     */
    @GetMapping("/{id}/phone")
    public ResponseEntity<PhoneNumberResponse> getSellerPhone(@RequestHeader("X-User-Id") String requesterId,
                                                              @PathVariable UUID id,
                                                              @RequestParam UUID carId) {
        // Audit trail for phone lookups; never log the number itself
        log.info("Phone number requested: requester={} seller={} car={}", requesterId, id, carId);

        return sellerPhoneService.findPhoneForListing(id, carId)
            .map(phone -> ResponseEntity.ok(new PhoneNumberResponse(phone)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Replace own phone number and address (phone, city and street required).
     *
     * @param userId  User ID from JWT (via gateway)
     * @param request Contact info with validation
     * @return Updated profile or 404 if not found
     */
    @PutMapping("/me/contact")
    public ResponseEntity<UserProfileResponse> updateContactInfo(@RequestHeader("X-User-Id") String userId,
                                                                 @Valid @RequestBody ContactInfoRequest request) {
        UUID id = UUID.fromString(userId);
        log.info("Updating contact info for user: {}", id);

        try {
            UserProfile updated = userProfileService.updateContactInfo(id, request);
            return ResponseEntity.ok(userProfileMapper.toResponse(updated));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update contact info for user {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
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
     * Meant for car-service when a user creates a listing (not called by any service yet).
     * Admin only (see SecurityConfig) — the gateway routes all of /api/users/**.
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
     * Meant for car-service when a user deletes a listing (not called by any service yet).
     * Admin only (see SecurityConfig) — the gateway routes all of /api/users/**.
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
