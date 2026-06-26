package com.carmarket.user.controller;

import com.carmarket.user.config.SecurityConfigTest;
import com.carmarket.user.dto.UserProfileRequest;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.mapper.UserProfileMapper;
import com.carmarket.user.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for UserController using JUnit5 and Mockito.
 * Tests all 6 endpoints with happy paths, validation errors, and exception handling.
 * <p>
 * Endpoints covered:
 * - GET  /api/users/me             ✓
 * - GET  /api/users/{id}           ✓
 * - PATCH /api/users/me            ✓
 * - DELETE /api/users/me           ✓
 * - POST /{userId}/increment-listings ✓
 * - POST /{userId}/decrement-listings ✓
 */

@WebMvcTest(UserController.class)
@Import(SecurityConfigTest.class)
@DisplayName("UserController Tests")
class UserControllerTest {

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private UserProfileMapper userProfileMapper;

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;
    private UserProfile testUser;
    private UserProfileResponse testUserResponse;
    private UserProfileRequest testUserRequest;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testUserId = UUID.randomUUID();
        testUser = UserProfile.builder()
            .id(testUserId)
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(5)
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now())
            .build();

        testUserResponse = UserProfileResponse.builder()
            .id(testUserId)
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(5)
            .createdAt(testUser.getCreatedAt())
            .updatedAt(testUser.getUpdatedAt())
            .build();

        testUserRequest = UserProfileRequest.builder()
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /me should return 200 with user profile when user exists")
    void testGetMyProfile_Success() throws Exception {
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));
        when(userProfileMapper.toResponse(testUser)).thenReturn(testUserResponse);

        mockMvc.perform(get("/users/me")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.role").value("USER"));

        verify(userProfileService, times(1)).getUserProfile(testUserId);
        verify(userProfileMapper, times(1)).toResponse(testUser);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /me should return 401 when user not found")
    void testGetMyProfile_NotFound() throws Exception {
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/me")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());

        verify(userProfileService, times(1)).getUserProfile(testUserId);
        verify(userProfileMapper, never()).toResponse(any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id} should return 200 with user profile")
    void testGetProfile_Success() throws Exception {
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));
        when(userProfileMapper.toResponse(testUser)).thenReturn(testUserResponse);

        mockMvc.perform(get("/users/{id}", testUserId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userProfileService, times(1)).getUserProfile(testUserId);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id} should return 404 when user not found")
    void testGetProfile_NotFound() throws Exception {
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/{id}", testUserId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        verify(userProfileService, times(1)).getUserProfile(testUserId);
    }

    @Test
    @WithMockUser
    @DisplayName("PATCH /me should return 200 with updated profile")
    void testUpdateProfile_Success() throws Exception {
        UserProfileRequest updateRequest = UserProfileRequest.builder()
            .email("updated@example.com")
            .displayName("Updated User")
            .phoneNumber("987654321")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .build();

        UserProfile updatedUser = UserProfile.builder()
            .id(testUserId)
            .email("updated@example.com")
            .displayName("Updated User")
            .phoneNumber("987654321")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(5)
            .createdAt(testUser.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        UserProfileResponse updatedResponse = UserProfileResponse.builder()
            .id(testUserId)
            .email("updated@example.com")
            .displayName("Updated User")
            .phoneNumber("987654321")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(5)
            .createdAt(testUser.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        when(userProfileMapper.toEntity(updateRequest)).thenReturn(updatedUser);
        when(userProfileService.updateProfile(testUserId, updatedUser)).thenReturn(updatedUser);
        when(userProfileMapper.toResponse(updatedUser)).thenReturn(updatedResponse);

        mockMvc.perform(patch("/users/me")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("updated@example.com"));

        verify(userProfileService, times(1)).updateProfile(testUserId, updatedUser);
    }

    @Test
    @WithMockUser
    @DisplayName("PATCH /me should return 404 when user not found")
    void testUpdateProfile_NotFound() throws Exception {
        UserProfileRequest updateRequest = testUserRequest;
        UserProfile entityRequest = testUser;

        when(userProfileMapper.toEntity(updateRequest)).thenReturn(entityRequest);
        when(userProfileService.updateProfile(testUserId, entityRequest))
            .thenThrow(new IllegalArgumentException("User not found"));

        mockMvc.perform(patch("/users/me")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isNotFound());

        verify(userProfileService, times(1)).updateProfile(testUserId, entityRequest);
    }

    @Test
    @WithMockUser
    @DisplayName("PATCH /me should return 400 when email is invalid")
    void testUpdateProfile_ValidationError_InvalidEmail() throws Exception {
        UserProfileRequest invalidRequest = UserProfileRequest.builder()
            .email("not-an-email")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .build();

        mockMvc.perform(patch("/users/me")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        verify(userProfileService, never()).updateProfile(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /me should return 204 No Content on success")
    void testDeleteProfile_Success() throws Exception {
        doNothing().when(userProfileService).deactivateProfile(testUserId);

        mockMvc.perform(delete("/users/me")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        verify(userProfileService, times(1)).deactivateProfile(testUserId);
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /me should return 404 when user not found")
    void testDeleteProfile_NotFound() throws Exception {
        doThrow(new IllegalArgumentException("User not found"))
            .when(userProfileService).deactivateProfile(testUserId);

        mockMvc.perform(delete("/users/me")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        verify(userProfileService, times(1)).deactivateProfile(testUserId);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{userId}/increment-listings should return 200")
    void testIncrementListings_Success() throws Exception {
        UserProfile incremented = UserProfile.builder()
            .id(testUserId)
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(6)
            .createdAt(testUser.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        UserProfileResponse incrementedResp = UserProfileResponse.builder()
            .id(testUserId)
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(6)
            .createdAt(testUser.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        doNothing().when(userProfileService).incrementListingsCount(testUserId);
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(incremented));
        when(userProfileMapper.toResponse(incremented)).thenReturn(incrementedResp);

        mockMvc.perform(post("/users/{userId}/increment-listings", testUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listingsCount").value(6));

        verify(userProfileService, times(1)).incrementListingsCount(testUserId);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{userId}/increment-listings should return 404 when user not found")
    void testIncrementListings_NotFound() throws Exception {
        doThrow(new IllegalArgumentException("User not found"))
            .when(userProfileService).incrementListingsCount(testUserId);

        mockMvc.perform(post("/users/{userId}/increment-listings", testUserId))
            .andExpect(status().isNotFound());

        verify(userProfileService, times(1)).incrementListingsCount(testUserId);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{userId}/decrement-listings should return 200")
    void testDecrementListings_Success() throws Exception {
        UserProfile decremented = UserProfile.builder()
            .id(testUserId)
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(4)
            .createdAt(testUser.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        UserProfileResponse decrementedResp = UserProfileResponse.builder()
            .id(testUserId)
            .email("test@example.com")
            .displayName("Test User")
            .phoneNumber("123456789")
            .city("Warsaw")
            .country("Poland")
            .bio("Test bio")
            .avatarUrl("https://example.com/avatar.jpg")
            .role(UserProfile.UserRole.USER)
            .active(true)
            .listingsCount(4)
            .createdAt(testUser.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        doNothing().when(userProfileService).decrementListingsCount(testUserId);
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(decremented));
        when(userProfileMapper.toResponse(decremented)).thenReturn(decrementedResp);

        mockMvc.perform(post("/users/{userId}/decrement-listings", testUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listingsCount").value(4));

        verify(userProfileService, times(1)).decrementListingsCount(testUserId);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{userId}/decrement-listings should return 404 when user not found")
    void testDecrementListings_NotFound() throws Exception {
        doThrow(new IllegalArgumentException("User not found"))
            .when(userProfileService).decrementListingsCount(testUserId);

        mockMvc.perform(post("/users/{userId}/decrement-listings", testUserId))
            .andExpect(status().isNotFound());

        verify(userProfileService, times(1)).decrementListingsCount(testUserId);
    }
}