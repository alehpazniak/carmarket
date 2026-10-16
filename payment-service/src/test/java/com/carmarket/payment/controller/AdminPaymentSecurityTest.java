package com.carmarket.payment.controller;

import com.carmarket.payment.config.SecurityConfig;
import com.carmarket.payment.security.GatewaySignatureFilter;
import com.carmarket.payment.service.AdminPaymentService;
import com.carmarket.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminPaymentController.class, PaymentController.class})
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=" + AdminPaymentSecurityTest.SECRET)
class AdminPaymentSecurityTest {

    static final String SECRET = "test-gateway-secret";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private AdminPaymentService adminService;
    @MockBean
    private PaymentService paymentService;

    @Test
    void everyAdminEndpointIsForbiddenToUsersAndOpenToAdmins() throws Exception {
        when(adminService.search(any(), any())).thenReturn(Page.empty());
        String id = UUID.randomUUID().toString();
        List<Supplier<MockHttpServletRequestBuilder>> requests = List.of(
            () -> get("/payments/admin/payments"), () -> get("/payments/admin/payments/" + id),
            () -> get("/payments/admin/payments.csv"), () -> get("/payments/admin/stats"),
            () -> post("/payments/" + id + "/refund").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"duplicate payment\"}"));
        for (var request : requests) {
            mvc.perform(request.get().with(as("USER"))).andExpect(status().isForbidden());
            mvc.perform(request.get()).andExpect(status().isForbidden());
            mvc.perform(request.get().with(as("ADMIN"))).andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    void csvIsAnAttachment() throws Exception {
        mvc.perform(get("/payments/admin/payments.csv").with(as("ADMIN")))
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"platnosci-")));
    }

    @Test
    void refundNeedsAReason() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(post("/payments/" + id + "/refund").contentType(MediaType.APPLICATION_JSON).content("{}").with(as("ADMIN")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/payments/" + id + "/refund").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"dup\"}").with(as("ADMIN")))
            .andExpect(status().isBadRequest());
        verify(paymentService, never()).refund(any(), any(), any(), any());
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
