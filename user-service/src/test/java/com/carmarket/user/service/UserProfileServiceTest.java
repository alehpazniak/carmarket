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
import java.util.List;
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

    @Test
    @DisplayName("getUserByEmail - should return empty when email not found")
    void testGetUserByEmail_NotFound() {
        // Arrange
        String email = "nonexistent@example.com";
        when(userProfileRepository.findByEmail(email))
            .thenReturn(Optional.empty());

        // Act
        Optional<UserProfile> result = userProfileService.getUserByEmail(email);

        // Assert
        assertFalse(result.isPresent());
        verify(userProfileRepository, times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("getUsersByRole - should return users with specific role")
    void testGetUsersByRole_Success() {
        UserProfile dealer = UserProfile.builder()
            .id(UUID.randomUUID())
            .email("dealer@example.com")
            .role(UserRole.DEALER)
            .active(true)
            .build();
        List<UserProfile> dealers = List.of(dealer);
        when(userProfileRepository.findByRoleAndActive(UserRole.DEALER, true)).thenReturn(dealers);

        List<UserProfile> result = userProfileService.getUsersByRole(UserRole.DEALER);

        assertEquals(1, result.size());
        assertEquals(UserRole.DEALER, result.get(0).getRole());
        verify(userProfileRepository, times(1)).findByRoleAndActive(UserRole.DEALER, true);
    }

    @Test
    @DisplayName("createProfile - should create new profile successfully")
    void testCreateProfile_Success() {
        when(userProfileRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userProfileRepository.save(testUserProfile)).thenReturn(testUserProfile);

        UserProfile result = userProfileService.createProfile(testUserProfile);

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(userProfileRepository, times(1)).findByEmail("test@example.com");
        verify((userProfileRepository), times(1)).save(testUserProfile);
    }
}