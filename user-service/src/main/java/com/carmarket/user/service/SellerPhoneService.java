package com.carmarket.user.service;

import com.carmarket.user.client.CarListingClient;
import com.carmarket.user.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * A seller's phone number is shown only in the context of one of their ACTIVE listings, so it
 * can't be harvested by walking user ids. Every failed check gives the same empty result.
 */
@Service
@RequiredArgsConstructor
public class SellerPhoneService {

    private static final String ACTIVE = "ACTIVE";

    private final UserProfileService userProfileService;
    private final CarListingClient carListingClient;

    /**
     * Empty when the listing is missing, not ACTIVE or someone else's, or the seller is
     * inactive or has no phone.
     *
     * @throws com.carmarket.user.exception.CarServiceUnavailableException if car-service can't be asked
     */
    public Optional<String> findPhoneForListing(UUID sellerId, UUID carId) {
        boolean listingOfSeller = carListingClient.findListing(carId)
            .filter(car -> ACTIVE.equals(car.status()) && sellerId.equals(car.sellerId()))
            .isPresent();
        if (!listingOfSeller) {
            return Optional.empty();
        }
        return userProfileService.getUserProfile(sellerId)
            .filter(UserProfile::isActive)
            .map(UserProfile::getPhoneNumber)
            .filter(phone -> !phone.isBlank());
    }
}
