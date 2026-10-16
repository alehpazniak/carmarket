package com.carmarket.notification.config;

import com.carmarket.notification.admin.AdminNotificationController;
import com.carmarket.notification.admin.AdminNotificationService;
import com.carmarket.notification.security.GatewaySignatureFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port 8087 is published: only signed admins reach the admin API, everything else is denied. */
@WebMvcTest(AdminNotificationController.class)
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=" + SecurityRulesTest.SECRET)
class SecurityRulesTest {

    static final String SECRET = "test-gateway-secret";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private AdminNotificationService service;

    @Test
    void adminEndpointsNeedASignedAdmin() throws Exception {
        when(service.list(any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());
        String id = UUID.randomUUID().toString();

        mvc.perform(get("/notifications/admin/notifications")).andExpect(status().isForbidden());
        mvc.perform(get("/notifications/admin/notifications").header("X-User-Id", UUID.randomUUID().toString())
            .header("X-User-Roles", "ADMIN")).andExpect(status().isForbidden());
        mvc.perform(get("/notifications/admin/notifications").with(as("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/notifications/admin/notifications/" + id + "/retry").with(as("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/notifications/admin/stats").with(as("USER"))).andExpect(status().isForbidden());

        mvc.perform(get("/notifications/admin/notifications").with(as("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/notifications/admin/notifications/" + id + "/retry").with(as("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(get("/notifications/admin/stats").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void everythingElseIsDenied() throws Exception {
        mvc.perform(get("/notifications").with(as("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/anything")).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor as(String roles) {
        return request -> {
            String userId = UUID.randomUUID().toString();
            try {
                long ts = System.currentTimeMillis();
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                String sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((userId + ":" + ts).getBytes(StandardCharsets.UTF_8)));
                request.addHeader("X-User-Id", userId);
                request.addHeader("X-User-Roles", roles);
                request.addHeader("X-Gateway-Signature", ts + ":" + sig);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return request;
        };
    }
}
