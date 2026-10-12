package com.carmarket.auction.controller;

import com.carmarket.auction.audit.AdminAuditPublisher;
import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.config.SecurityConfig;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.scheduler.LotSyncScheduler;
import com.carmarket.auction.security.GatewaySignatureFilter;
import com.carmarket.auction.service.CurrencyExchangeService;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuctionController.class)
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=" + AdminAuctionSecurityTest.SECRET)
class AdminAuctionSecurityTest {

    static final String SECRET = "test-gateway-secret";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ApibaraClient apibara;
    @MockBean
    private SyncRunRepository runs;
    @MockBean
    private LotSyncScheduler scheduler;
    @MockBean
    private CurrencyExchangeService currency;
    @MockBean
    private AdminAuditPublisher audit;

    @Test
    void statusAndSyncAreAdminOnly() throws Exception {
        when(currency.lastRate()).thenReturn(Optional.empty());
        mvc.perform(get("/api/auctions/admin/status").with(as("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auctions/admin/sync").with(as("DEALER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auctions/admin/status")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auctions/admin/status").with(as("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apibaraConfigured").value(false));
        verify(scheduler, never()).triggerManualSync();
    }

    @Test
    void syncIsStartedOnceAndConflictsWhileRunning() throws Exception {
        when(scheduler.triggerManualSync()).thenReturn(true, false);

        mvc.perform(post("/api/auctions/admin/sync").with(as("ADMIN"))).andExpect(status().isAccepted());
        mvc.perform(post("/api/auctions/admin/sync").with(as("ADMIN"))).andExpect(status().isConflict());
        verify(audit).publish(any(), eq("AUCTION_SYNC_TRIGGERED"), eq("AUCTION_SYNC"), any(), any(), any());
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
