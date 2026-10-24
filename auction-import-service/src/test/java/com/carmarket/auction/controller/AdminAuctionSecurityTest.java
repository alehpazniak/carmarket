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
import static org.mockito.ArgumentMatchers.isNull;
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
    @MockBean
    private com.carmarket.auction.sync.SyncBudget budget;

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
        verify(scheduler, never()).triggerManualSync(any());
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
        when(scheduler.triggerManualSync(isNull())).thenReturn(true, false);

        mvc.perform(post("/api/auctions/admin/sync").with(as("ADMIN"))).andExpect(status().isAccepted());
        mvc.perform(post("/api/auctions/admin/sync").with(as("ADMIN"))).andExpect(status().isConflict());
        verify(audit).publish(any(), eq("AUCTION_SYNC_TRIGGERED"), eq("AUCTION_SYNC"), any(), any(), any());
    }

    /** 1 … what the period still allows (here 23), even beyond today's allowance of 7 */
    @Test
    void aManualLimitMustFitThePeriod() throws Exception {
        when(budget.today()).thenReturn(new com.carmarket.auction.sync.SyncBudget.Allowance(null, null, 8, 37, 0, 7, 23, 7, 23, 23));
        when(scheduler.triggerManualSync(23)).thenReturn(true);

        mvc.perform(post("/api/auctions/admin/sync").param("maxRequests", "24").with(as("ADMIN"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Liczba zapytań musi być od 1 do 23"));
        mvc.perform(post("/api/auctions/admin/sync").param("maxRequests", "0").with(as("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auctions/admin/sync").param("maxRequests", "23").with(as("ADMIN"))).andExpect(status().isAccepted());

        verify(scheduler).triggerManualSync(23);
        verify(audit).publish(any(), eq("AUCTION_SYNC_TRIGGERED"), eq("AUCTION_SYNC"), any(), any(), eq(java.util.Map.of("maxRequests", 23)));
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
