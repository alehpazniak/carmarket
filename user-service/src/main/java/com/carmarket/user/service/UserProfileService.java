package com.carmarket.user.service;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user profiles.
 * Handles CRUD operations and business logic.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    /**
     * Retrieve a user profile by ID.
     *
     * @param id User ID
     * @return User profile if found
     */
    public Optional<UserProfile> getUserProfile(UUID id) {
        return userProfileRepository.findById(id);
    }

    /**
     * Retrieve user by email.
     *
     * @param email User email
     * @return User profile if found
     */
    public Optional<UserProfile> getUserByEmail(String email) {
        return userProfileRepository.findByEmail(email);
    }

    /**
     * Get all active users with a specific role.
     *
     * @param role User role
     * @return List of users with the role
     */
    public List<UserProfile> getUsersByRole(UserProfile.UserRole role) {
        return userProfileRepository.findByRoleAndActive(role, true);
    }

    /**
     * Create a new user profile.
     *
     * @param userProfile Profile to create
     * @return Created profile
     */
    @Transactional
    public UserProfile createProfile(UserProfile userProfile) {
        if (userProfileRepository.findByEmail(userProfile.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + userProfile.getEmail());
        }
        return userProfileRepository.save(userProfile);
    }

    /**
     * Update an existing user profile.
     *
     * @param id      User ID
     * @param updates Profile with updated fields
     * @return Updated profile
     */
    @Transactional
    public UserProfile updateProfile(UUID id, UserProfile updates) {
        UserProfile existing = userProfileRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        if (updates.getDisplayName() != null) {
            existing.setDisplayName(updates.getDisplayName());
        }
        if (updates.getPhoneNumber() != null) {
            existing.setPhoneNumber(updates.getPhoneNumber());
        }
        if (updates.getCity() != null) {
            existing.setCity(updates.getCity());
        }
        if (updates.getCountry() != null) {
            existing.setCountry(updates.getCountry());
        }
        if (updates.getBio() != null) {
            existing.setBio(updates.getBio());
        }
        if (updates.getAvatarUrl() != null) {
            existing.setAvatarUrl(updates.getAvatarUrl());
        }

        return userProfileRepository.save(existing);
    }

    /**
     * Delete a user profile.
     *
     * @param id User ID
     */
    @Transactional
    public void deleteProfile(UUID id) {
        userProfileRepository.deleteById(id);
    }

    /**
     * Deactivate a user (soft delete).
     *
     * @param id User ID
     */
    @Transactional
    public void deactivateProfile(UUID id) {
        UserProfile profile = userProfileRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        profile.setActive(false);
        userProfileRepository.save(profile);
    }

    /**
     * Increment listings count for a user.
     *
     * @param id User ID
     */
    @Transactional
    public void incrementListingsCount(UUID id) {
        UserProfile profile = userProfileRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        profile.setListingsCount(profile.getListingsCount() + 1);
        userProfileRepository.save(profile);
    }

    /**
     * Decrement listings count for a user.
     *
     * @param id User ID
     */
    @Transactional
    public void decrementListingsCount(UUID id) {
        UserProfile profile = userProfileRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (profile.getListingsCount() > 0) {
            profile.setListingsCount(profile.getListingsCount() - 1);
            userProfileRepository.save(profile);
        }
    }
}
