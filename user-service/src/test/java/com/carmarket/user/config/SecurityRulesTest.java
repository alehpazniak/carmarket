package com.carmarket.user.config;

import com.carmarket.user.controller.UserController;
import com.carmarket.user.service.SellerPhoneService;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.mapper.UserProfileMapper;
import com.carmarket.user.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real {@link SecurityConfig} + GatewaySignatureFilter (unlike {@link SecurityConfigTest},
 * which permits everything) with requests signed the way the gateway signs them.
 */
@WebMvcTest({UserController.class, com.carmarket.user.controller.DealerPageController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "gateway.internal-secret=" + SecurityRulesTest.SECRET)
@DisplayName("user-service security rules")
class SecurityRulesTest {

    static final String SECRET = "test-gateway-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private UserProfileMapper userProfileMapper;

    @MockBean
    private SellerPhoneService sellerPhoneService;

    @MockBean
    private com.carmarket.user.service.DealerPageService dealerPageService;

    private final UUID targetUserId = UUID.randomUUID();

    @Test
    @DisplayName("increment-listings: a regular user gets 403")
    void incrementListings_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(signed(post("/users/{id}/increment-listings", targetUserId), "USER"))
            .andExpect(status().isForbidden());
        verify(userProfileService, never()).incrementListingsCount(any());
    }

    @Test
    @DisplayName("decrement-listings: a regular user gets 403")
    void decrementListings_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(signed(post("/users/{id}/decrement-listings", targetUserId), "USER", "DEALER"))
            .andExpect(status().isForbidden());
        verify(userProfileService, never()).decrementListingsCount(any());
    }

    @Test
    @DisplayName("increment-listings: unsigned request (gateway bypassed) gets 403")
    void incrementListings_unsigned_forbidden() throws Exception {
        mockMvc.perform(post("/users/{id}/increment-listings", targetUserId)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isForbidden());
        verify(userProfileService, never()).incrementListingsCount(any());
    }

    @Test
    @DisplayName("increment-listings: an admin is allowed")
    void incrementListings_admin_ok() throws Exception {
        when(userProfileService.getUserProfile(targetUserId)).thenReturn(Optional.of(new UserProfile()));
        when(userProfileMapper.toResponse(any())).thenReturn(new UserProfileResponse());

        mockMvc.perform(signed(post("/users/{id}/increment-listings", targetUserId), "ADMIN"))
            .andExpect(status().isOk());
        verify(userProfileService).incrementListingsCount(targetUserId);
    }

    @Test
    @DisplayName("phone: unsigned request (gateway bypassed) gets 403")
    void phone_unsigned_forbidden() throws Exception {
        mockMvc.perform(get("/users/{id}/phone", targetUserId)
                .param("carId", UUID.randomUUID().toString())
                .header("X-User-Id", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        verify(sellerPhoneService, never()).findPhoneForListing(any(), any());
    }

    @Test
    @DisplayName("phone: a signed request reaches the endpoint")
    void phone_signed_ok() throws Exception {
        UUID carId = UUID.randomUUID();
        when(sellerPhoneService.findPhoneForListing(targetUserId, carId)).thenReturn(Optional.of("123456789"));

        mockMvc.perform(signed(get("/users/{id}/phone", targetUserId).param("carId", carId.toString()), "USER"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("public profile: unsigned request is allowed")
    void publicProfile_unsigned_ok() throws Exception {
        when(userProfileService.getUserProfile(targetUserId))
            .thenReturn(Optional.of(UserProfile.builder().id(targetUserId).email("x@example.com")
                .role(UserProfile.UserRole.USER).build()));

        mockMvc.perform(get("/users/{id}", targetUserId))
            .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String... roles) throws Exception {
        String userId = UUID.randomUUID().toString();
        long ts = System.currentTimeMillis();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal((userId + ":" + ts).getBytes(StandardCharsets.UTF_8)));
        return request
            .header("X-User-Id", userId)
            .header("X-User-Roles", String.join(",", roles))
            .header("X-Gateway-Signature", ts + ":" + sig);
    }

    @Test
    @DisplayName("dealer pages and their phone are public; unknown slug is 404")
    void dealerPage_public() throws Exception {
        when(dealerPageService.findPublic("auto-kowalski")).thenReturn(Optional.empty());
        when(dealerPageService.findPublicPhone("auto-kowalski")).thenReturn(Optional.of("+48 600 100 200"));

        mockMvc.perform(get("/users/dealers/auto-kowalski")).andExpect(status().isNotFound());
        mockMvc.perform(get("/users/dealers/auto-kowalski/phone")).andExpect(status().isOk());
        when(dealerPageService.findPublicByUserId(any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/users/dealers/by-user/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("own dealer page: unsigned request (gateway bypassed) gets 403")
    void ownDealerPage_unsigned_forbidden() throws Exception {
        mockMvc.perform(get("/users/me/dealer-page").header("X-User-Id", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/users/me/dealer-page")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        verify(dealerPageService, never()).save(any(), any());
    }

    @Test
    @DisplayName("own dealer page: signed request reaches the service; invalid slug is a 400")
    void ownDealerPage_signed() throws Exception {
        when(dealerPageService.findOwn(any())).thenReturn(Optional.empty());
        mockMvc.perform(signed(get("/users/me/dealer-page"), "DEALER")).andExpect(status().isNotFound());

        mockMvc.perform(signed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/users/me/dealer-page")
                .contentType("application/json")
                .content("{\"slug\":\"-Bad Slug-\",\"companyName\":\"Auto\",\"published\":true}"), "DEALER"))
            .andExpect(status().isBadRequest());
        verify(dealerPageService, never()).save(any(), any());
    }
}
