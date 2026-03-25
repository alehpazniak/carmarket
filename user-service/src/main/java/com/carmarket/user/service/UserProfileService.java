package com.carmarket.user.service;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.exception.UserProfileNotFoundException;
import com.carmarket.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfile fetchMyProfile(String userId) {
        return fetchProfile(parseUuid(userId));
    }

    public UserProfile fetchProfile(UUID userId) {
        return userProfileRepository.findById(userId)
            .orElseThrow(() -> new UserProfileNotFoundException("User with id: " + userId + " wasn't found"));
    }

    public UserProfile updateUserProfile(String userId, Map<String, String> updates) {
        UserProfile user = userProfileRepository.findById(parseUuid(userId))
            .orElseThrow(() -> new UserProfileNotFoundException("User with id: " + userId + " wasn't found"));
        applyUpdates(user, updates);
        return userProfileRepository.save(user);
    }

    private UUID parseUuid(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid userId format: " + userId, ex);
        }
    }

    private void applyUpdates(UserProfile user, Map<String, String> updates) {
        Map<String, Consumer<String>> fieldUpdaters = Map.of(
            "displayName", user::setDisplayName,
            "phoneNumber", user::setPhoneNumber,
            "city", user::setCity,
            "country", user::setCountry,
            "bio", user::setBio
        );

        updates.forEach((key, value) -> {
            Consumer<String> updater = fieldUpdaters.get(key);
            if (updater != null) {
                updater.accept(value);
            } else {
                throw new IllegalArgumentException("Unknown field: " + key);
            }
        });
    }
}
