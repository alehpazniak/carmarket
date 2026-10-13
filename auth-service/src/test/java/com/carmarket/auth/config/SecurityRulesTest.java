package com.carmarket.auth.config;

import com.carmarket.auth.controller.AuthController;
import com.carmarket.auth.security.GatewaySignatureFilter;
import com.carmarket.auth.security.OAuth2FailureHandler;
import com.carmarket.auth.security.OAuth2SuccessHandler;
import com.carmarket.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real SecurityConfig + GatewaySignatureFilter: port 8081 is published, so X-User-Id alone must not work. */
@WebMvcTest({AuthController.class, com.carmarket.auth.controller.AdminUserController.class})
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=" + SecurityRulesTest.SECRET)
class SecurityRulesTest {

    static final String SECRET = "test-gateway-secret";
    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private OAuth2SuccessHandler successHandler;
    @MockBean
    private OAuth2FailureHandler failureHandler;
    @MockBean
    private com.carmarket.auth.service.AdminUserService adminUserService;

    @Test
    void logoutAllWithAForgedUserIdIsRejected() throws Exception {
        mvc.perform(post("/auth/logout-all").header("X-User-Id", USER.toString()))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").header("X-User-Id", USER.toString()))
            .andExpect(status().isUnauthorized());
        verify(authService, never()).logoutAllDevices(any());
    }

    @Test
    void logoutAllWorksForTheSignedCaller() throws Exception {
        mvc.perform(post("/auth/logout-all").with(as(USER, "USER"))).andExpect(status().isOk());
        verify(authService).logoutAllDevices(USER.toString());
    }

    @Test
    void adminPathsNeedTheAdminRole() throws Exception {
        mvc.perform(get("/auth/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/admin/users").header("X-User-Id", USER.toString()).header("X-User-Roles", "ADMIN"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/admin/users").with(as(USER, "USER"))).andExpect(status().isForbidden());
    }

    @Test
    void everyAdminEndpointIsForbiddenToUsersAndOpenToAdmins() throws Exception {
        org.mockito.Mockito.when(adminUserService.search(any(), any(), any(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());
        org.mockito.Mockito.when(adminUserService.dealerRequests(any(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());
        String id = UUID.randomUUID().toString();
        String reason = "{\"reason\":\"spam account\"}";
        String role = "{\"role\":\"DEALER\",\"reason\":\"verified dealer\"}";
        // Builders are mutable (with() adds headers), so each call gets a fresh one
        java.util.List<java.util.function.Supplier<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>> requests = java.util.List.of(
            () -> get("/auth/admin/users"), () -> get("/auth/admin/users/" + id), () -> get("/auth/admin/dealer-requests"),
            () -> get("/auth/admin/stats"),
            () -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/auth/admin/users/" + id + "/role")
                .contentType(MediaType.APPLICATION_JSON).content(role),
            () -> post("/auth/admin/users/" + id + "/block").contentType(MediaType.APPLICATION_JSON).content(reason),
            () -> post("/auth/admin/users/" + id + "/unblock").contentType(MediaType.APPLICATION_JSON).content(reason));
        for (var request : requests) {
            mvc.perform(request.get().with(as(USER, "USER"))).andExpect(status().isForbidden());
            mvc.perform(request.get().with(as(USER, "ADMIN"))).andExpect(status().is2xxSuccessful());
        }
        mvc.perform(post("/auth/admin/users/" + id + "/logout-all").contentType(MediaType.APPLICATION_JSON).content(reason)
            .with(as(USER, "USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/auth/admin/users/" + id + "/logout-all").contentType(MediaType.APPLICATION_JSON).content(reason)
            .with(as(USER, "ADMIN"))).andExpect(status().isNoContent());
    }

    @Test
    void destructiveAdminActionsNeedAReason() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(post("/auth/admin/users/" + id + "/block").contentType(MediaType.APPLICATION_JSON).content("{}")
            .with(as(USER, "ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(post("/auth/admin/users/" + id + "/block").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"spam\"}")
            .with(as(USER, "ADMIN"))).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(adminUserService);
    }

    @Test
    void refreshAndLogoutStayPublic() throws Exception {
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_token\":\"x\"}"))
            .andExpect(status().isOk());
    }

    static RequestPostProcessor as(UUID userId, String roles) {
        return request -> {
            request.addHeader("X-User-Id", userId.toString());
            request.addHeader("X-User-Roles", roles);
            request.addHeader("X-Gateway-Signature", sign(userId.toString()));
            return request;
        };
    }

    private static String sign(String userId) {
        try {
            long ts = System.currentTimeMillis();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((userId + ":" + ts).getBytes(StandardCharsets.UTF_8));
            return ts + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
