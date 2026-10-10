package com.carmarket.user.config;

import com.carmarket.user.controller.UserController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real {@link SecurityConfig} + GatewaySignatureFilter (unlike {@link SecurityConfigTest},
 * which permits everything) with requests signed the way the gateway signs them.
 */
@WebMvcTest(UserController.class)
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
}
