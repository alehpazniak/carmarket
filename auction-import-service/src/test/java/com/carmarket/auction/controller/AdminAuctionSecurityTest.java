package com.carmarket.auction.controller;

import com.carmarket.auction.audit.AdminAuditPublisher;
import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraQuota;
import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.config.SecurityConfig;
import com.carmarket.auction.config.W8ShippingConfig;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.repository.W8TowingRateRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
@Import({SecurityConfig.class, GatewaySignatureFilter.class, W8ShippingConfig.class})
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
    @MockBean
    private W8TowingRateRepository w8Rates;
    @MockBean
    private ApibaraQuotaTracker quotaTracker;
    @MockBean
    private com.carmarket.auction.sync.FeedSyncService feedSync;

    @Test
    void statusAndSyncAreAdminOnly() throws Exception {
        when(currency.lastRate()).thenReturn(Optional.empty());
        when(w8Rates.countByPriceList("W8-2025-26")).thenReturn(725L);
        mvc.perform(get("/api/auctions/admin/status").with(as("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auctions/admin/sync").with(as("DEALER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auctions/admin/status")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auctions/admin/status").with(as("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apibaraConfigured").value(false))
            .andExpect(jsonPath("$.w8PriceList").value("W8-2025-26"))
            .andExpect(jsonPath("$.w8TowingRows").value(725));
        verify(scheduler, never()).triggerManualSync();
    }

    @Test
    void statusShowsTheSyncQuotaAndCallsUsageOnlyOnRefresh() throws Exception {
        when(currency.lastRate()).thenReturn(Optional.empty());
        when(apibara.isConfigured()).thenReturn(true);
        when(apibara.getUsage()).thenReturn(new ObjectMapper().readTree("{\"data\": {\"plan\": \"free\"}}"));
        when(quotaTracker.latest()).thenReturn(Optional.of(new ApibaraQuota(100, 58, 42,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-31T23:59:59Z"), 9, 20,
            Instant.parse("2026-10-21T10:00:00Z"))));

        mvc.perform(get("/api/auctions/admin/status").with(as("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apibaraQuota.remaining").value(42))
            .andExpect(jsonPath("$.apibaraQuota.periodEnd").value("2026-10-31T23:59:59Z"))
            .andExpect(jsonPath("$.apibaraUsage").isEmpty());
        verify(apibara, never()).getUsage();

        mvc.perform(get("/api/auctions/admin/status").param("refresh", "true").with(as("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apibaraUsage.data.plan").value("free"));
        verify(apibara).getUsage();
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
