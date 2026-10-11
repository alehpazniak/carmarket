package com.carmarket.user.controller;

import com.carmarket.user.client.CarListingClient;
import com.carmarket.user.client.CarListingClient.CarListingSummary;
import com.carmarket.user.config.SecurityConfigTest;
import com.carmarket.user.dto.ContactInfoRequest;
import com.carmarket.user.dto.UserProfileRequest;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.exception.CarServiceUnavailableException;
import com.carmarket.user.mapper.UserProfileMapper;
import com.carmarket.user.service.SellerPhoneService;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * - GET  /api/users/{id}/phone     ✓ (real SellerPhoneService, mocked car-service client)
 * - PATCH /api/users/me            ✓
 * - DELETE /api/users/me           ✓
 * - POST /{userId}/increment-listings ✓
 * - POST /{userId}/decrement-listings ✓
 */

@WebMvcTest(UserController.class)
@Import({SecurityConfigTest.class, SellerPhoneService.class})
@DisplayName("UserController Tests")
class UserControllerTest {

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private UserProfileMapper userProfileMapper;

    @MockBean
    private CarListingClient carListingClient;

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
    @DisplayName("GET /{id} should return 200 with only the public profile fields")
    void testGetProfile_Success() throws Exception {
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/users/{id}", testUserId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"))
            .andExpect(jsonPath("$.city").value("Warsaw"))
            .andExpect(jsonPath("$.bio").value("Test bio"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.memberSince").exists());

        verify(userProfileService, times(1)).getUserProfile(testUserId);
        verify(userProfileMapper, never()).toResponse(any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id} should return 404 for a deactivated user")
    void testGetProfile_Inactive() throws Exception {
        testUser.setActive(false);
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/users/{id}", testUserId))
            .andExpect(status().isNotFound());
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
    @DisplayName("PUT /me/contact should return 200 with saved phone and address")
    void testUpdateContactInfo_Success() throws Exception {
        ContactInfoRequest request = ContactInfoRequest.builder()
            .phoneNumber("+48 123 456 789").city("Warsaw").street("Marszałkowska").build();
        testUserResponse.setStreet("Marszałkowska");

        when(userProfileService.updateContactInfo(eq(testUserId), any(ContactInfoRequest.class))).thenReturn(testUser);
        when(userProfileMapper.toResponse(testUser)).thenReturn(testUserResponse);

        mockMvc.perform(put("/users/me/contact")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.street").value("Marszałkowska"));

        verify(userProfileService, times(1)).updateContactInfo(eq(testUserId), any(ContactInfoRequest.class));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /me/contact should return 400 with field errors when phone, city and street are missing")
    void testUpdateContactInfo_ValidationError_MissingAddress() throws Exception {
        ContactInfoRequest request = ContactInfoRequest.builder().houseNumber("12").build();

        mockMvc.perform(put("/users/me/contact")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.city").value("City is required"))
            .andExpect(jsonPath("$.street").value("Street is required"))
            .andExpect(jsonPath("$.phoneNumber").value("Phone number is required"));

        verify(userProfileService, never()).updateContactInfo(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /me/contact should return 400 when phone number has invalid format")
    void testUpdateContactInfo_ValidationError_InvalidPhone() throws Exception {
        ContactInfoRequest request = ContactInfoRequest.builder()
            .phoneNumber("abc").city("Warsaw").street("Marszałkowska").build();

        mockMvc.perform(put("/users/me/contact")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.phoneNumber").exists());

        verify(userProfileService, never()).updateContactInfo(any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id} should not expose private fields")
    void testGetProfile_HidesPrivateFields() throws Exception {
        testUser.setStreet("Marszałkowska");
        testUser.setHouseNumber("12");
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/users/{id}", testUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.city").value("Warsaw"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.street").doesNotExist())
            .andExpect(jsonPath("$.houseNumber").doesNotExist())
            .andExpect(jsonPath("$.country").doesNotExist())
            .andExpect(jsonPath("$.active").doesNotExist())
            .andExpect(jsonPath("$.listingsCount").doesNotExist())
            .andExpect(jsonPath("$.createdAt").doesNotExist())
            .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    // ─── GET /{id}/phone (real SellerPhoneService, mocked car-service client) ───

    private final UUID carId = UUID.randomUUID();

    private void listing(UUID sellerId, String status) {
        when(carListingClient.findListing(carId)).thenReturn(Optional.of(new CarListingSummary(carId, sellerId, status)));
    }

    private ResultActions requestPhone(Object carIdParam) throws Exception {
        return mockMvc.perform(get("/users/{id}/phone", testUserId)
            .param("carId", String.valueOf(carIdParam))
            .header("X-User-Id", UUID.randomUUID().toString()));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns the phone for the seller's ACTIVE listing")
    void testGetSellerPhone_Success() throws Exception {
        listing(testUserId, "ACTIVE");
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        requestPhone(carId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phoneNumber").value("123456789"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns 404 when the listing isn't ACTIVE")
    void testGetSellerPhone_ListingNotActive() throws Exception {
        listing(testUserId, "SOLD");
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        requestPhone(carId).andExpect(status().isNotFound()).andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns 404 when the listing belongs to someone else")
    void testGetSellerPhone_SellerMismatch() throws Exception {
        listing(UUID.randomUUID(), "ACTIVE");
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        requestPhone(carId).andExpect(status().isNotFound()).andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns 404 when the listing doesn't exist")
    void testGetSellerPhone_ListingNotFound() throws Exception {
        when(carListingClient.findListing(carId)).thenReturn(Optional.empty());
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        requestPhone(carId).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns 404 when the seller has no phone")
    void testGetSellerPhone_NoPhone() throws Exception {
        listing(testUserId, "ACTIVE");
        testUser.setPhoneNumber(null);
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        requestPhone(carId).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns 404 when the seller is deactivated")
    void testGetSellerPhone_InactiveSeller() throws Exception {
        listing(testUserId, "ACTIVE");
        testUser.setActive(false);
        when(userProfileService.getUserProfile(testUserId)).thenReturn(Optional.of(testUser));

        requestPhone(carId).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns 503 when car-service is down")
    void testGetSellerPhone_CarServiceDown() throws Exception {
        when(carListingClient.findListing(carId)).thenThrow(new CarServiceUnavailableException("Listing service is unavailable"));

        requestPhone(carId).andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{id}/phone returns 400 for an invalid or missing carId")
    void testGetSellerPhone_BadCarId() throws Exception {
        requestPhone("not-a-uuid").andExpect(status().isBadRequest());
        mockMvc.perform(get("/users/{id}/phone", testUserId).header("X-User-Id", UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(carListingClient);
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