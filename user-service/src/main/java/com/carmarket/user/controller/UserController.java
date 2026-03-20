package com.carmarket.user.controller;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.repository.UserProfileRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileRepository userProfileRepository;

    /** Get current user's profile — userId comes from gateway JWT header */
    @GetMapping("/me")
    public ResponseEntity<UserProfile> getMyProfile(
        @RequestHeader("X-User-Id") String userId) {
        return userProfileRepository.findById(UUID.fromString(userId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Get any user's public profile */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> getProfile(@PathVariable UUID id) {
        return userProfileRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Update own profile */
    @PatchMapping("/me")
    public ResponseEntity<UserProfile> updateProfile(
        @RequestHeader("X-User-Id") String userId,
        @RequestBody Map<String, String> updates) {

        return userProfileRepository.findById(UUID.fromString(userId))
            .map(profile -> {
                if (updates.containsKey("displayName")) profile.setDisplayName(updates.get("displayName"));
                if (updates.containsKey("phoneNumber")) profile.setPhoneNumber(updates.get("phoneNumber"));
                if (updates.containsKey("city")) profile.setCity(updates.get("city"));
                if (updates.containsKey("country")) profile.setCountry(updates.get("country"));
                if (updates.containsKey("bio")) profile.setBio(updates.get("bio"));
                return ResponseEntity.ok(userProfileRepository.save(profile));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
