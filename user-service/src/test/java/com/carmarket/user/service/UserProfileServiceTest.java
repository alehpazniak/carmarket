package com.carmarket.user.service;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.entity.UserProfile.UserRole;
import com.carmarket.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService Tests")
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private UUID testUserId;
    private UserProfile testUserProfile;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUserProfile = UserProfile.builder()
            .id(testUserId)
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserRole.USER)
            .active(true)
            .listingsCount(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("getUserProfile - should return user when found")
    void testGetUserProfile() {
        when(userProfileRepository.findById(testUserId)).thenReturn(Optional.of(testUserProfile));

        Optional<UserProfile> result = userProfileService.getUserProfile(testUserId);

        assertTrue(result.isPresent());
        assertEquals(testUserProfile.getId(), result.get().getId());
        assertEquals("test@example.com", result.get().getEmail());
        verify(userProfileRepository, times(1)).findById(testUserId);
    }

    @Test
    @DisplayName("getUserProfile - should return empty when user not found")
    void testGetUserProfile_NotFound() {
        when(userProfileRepository.findById(testUserId)).thenReturn(Optional.empty());

        Optional<UserProfile> result = userProfileService.getUserProfile(testUserId);

        assertFalse(result.isPresent());
        verify(userProfileRepository, times(1)).findById(testUserId);
    }

    @Test
    @DisplayName("getUserByEmail - should return user when email exists")
    void testGetUserByEmail_Found() {
        String email = "test@example.com";
        when(userProfileRepository.findByEmail(email)).thenReturn(Optional.of(testUserProfile));

        Optional<UserProfile> result = userProfileService.getUserByEmail(email);

        assertTrue(result.isPresent());
        assertEquals(email, result.get().getEmail());
        verify(userProfileRepository, times(1)).findByEmail(email);
    }
}