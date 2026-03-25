package com.carmarket.user.controller;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    /**
     * Get current user's profile — userId comes from gateway JWT header
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfile> getMyProfile(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(userProfileService.fetchMyProfile(userId));
    }

    /**
     * Get any user's public profile
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> getProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(userProfileService.fetchProfile(id));
    }

    /**
     * Update own profile
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfile> updateProfile(@RequestHeader("X-User-Id") String userId,
                                                     @RequestBody Map<String, String> updates) {
        return ResponseEntity.ok(userProfileService.updateUserProfile(userId, updates));
    }
}
