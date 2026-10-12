package com.carmarket.user.controller;

import com.carmarket.user.audit.AdminAuditPublisher;
import com.carmarket.user.config.SecurityConfig;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.mapper.UserProfileMapper;
import com.carmarket.user.repository.UserProfileRepository;
import com.carmarket.user.security.GatewaySignatureFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserProfileController.class)
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=" + AdminUserProfileControllerTest.SECRET)
class AdminUserProfileControllerTest {

    static final String SECRET = "test-gateway-secret";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private UserProfileRepository repository;
    @MockBean
    private UserProfileMapper mapper;
    @MockBean
    private AdminAuditPublisher audit;

    private final UUID target = UUID.randomUUID();

    @Test
    void adminsSeeTheFullProfileOfBlockedUsersTooAndTheReadIsAudited() throws Exception {
        UserProfile profile = UserProfile.builder().id(target).email("jan@example.com").role(UserProfile.UserRole.USER)
            .active(false).build();
        when(repository.findById(target)).thenReturn(Optional.of(profile));
        when(mapper.toResponse(profile)).thenReturn(UserProfileResponse.builder().id(target).email("jan@example.com")
            .phoneNumber("+48 600 000 000").active(false).build());

        mvc.perform(get("/users/admin/users/" + target).with(as("ADMIN")).header("X-User-Email", "boss@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phoneNumber").value("+48 600 000 000"));
        verify(audit).publish(argThat(a -> "boss@example.com".equals(a.email())), eq("USER_PII_VIEWED"), eq("USER"),
            eq(target.toString()), isNull(), eq(Map.of()));
    }

    @Test
    void usersAndUnsignedCallersAreForbidden() throws Exception {
        mvc.perform(get("/users/admin/users/" + target).with(as("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/users/admin/users/" + target).header("X-User-Id", UUID.randomUUID().toString())
            .header("X-User-Roles", "ADMIN")).andExpect(status().isForbidden());
        verify(repository, never()).findById(any());
        verify(audit, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownUserIs404AndNotAudited() throws Exception {
        when(repository.findById(target)).thenReturn(Optional.empty());
        mvc.perform(get("/users/admin/users/" + target).with(as("ADMIN"))).andExpect(status().isNotFound());
        verify(audit, never()).publish(any(), any(), any(), any(), any(), any());
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
