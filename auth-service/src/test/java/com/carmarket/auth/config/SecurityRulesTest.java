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
@WebMvcTest(AuthController.class)
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
