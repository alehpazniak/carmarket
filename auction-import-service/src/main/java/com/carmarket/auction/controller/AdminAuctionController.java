package com.carmarket.auction.controller;

import com.carmarket.auction.audit.AdminAuditPublisher;
import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraQuota;
import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.config.W8ShippingConfig;
import com.carmarket.auction.entity.SyncRun;
import com.carmarket.auction.repository.SyncRunRepository;
import com.carmarket.auction.repository.W8TowingRateRepository;
import com.carmarket.auction.scheduler.LotSyncScheduler;
import com.carmarket.auction.service.CurrencyExchangeService;
import com.carmarket.auction.sync.FeedSyncService;
import com.carmarket.auction.sync.SyncBudget;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Gateway: /api/auctions/admin/** — ADMIN only (SecurityConfig), never public (JwtAuthenticationFilter). */
@Slf4j
@RestController
@RequestMapping("/api/auctions/admin")
@RequiredArgsConstructor
public class AdminAuctionController {

    private final ApibaraClient apibaraClient;
    private final SyncRunRepository runs;
    private final LotSyncScheduler scheduler;
    private final CurrencyExchangeService currency;
    private final AdminAuditPublisher auditPublisher;
    private final W8TowingRateRepository w8Rates;
    private final W8ShippingConfig w8Config;
    private final ApibaraQuotaTracker quotaTracker;
    private final FeedSyncService feedSync;
    private final SyncBudget budget;

    /**
     * apibaraQuota: the sync's latest quota headers (free). apibaraUsage: GET /usage, only on ?refresh=true,
     * because Apibara doesn't document that /usage is exempt from the monthly quota.
     */
    public record AuctionStatus(boolean apibaraConfigured, ApibaraQuota apibaraQuota, JsonNode apibaraUsage,
                                String apibaraUsageError, boolean syncRunning, List<SyncRun> lastRuns, CurrencyExchangeService.LastRate exchangeRate,
                                String w8PriceList, long w8TowingRows, FeedSyncService.FeedStatus feed,
                                Instant nextScheduledRun) {
    }

    @GetMapping("/status")
    public AuctionStatus status(@RequestParam(defaultValue = "false") boolean refresh) {
        boolean configured = apibaraClient.isConfigured();
        JsonNode usage = null;
        String usageError = null;
        if (configured && refresh) {
            try {
                usage = apibaraClient.getUsage();
                if (usage == null) usageError = "Apibara did not answer";
            } catch (RuntimeException e) {
                log.warn("Apibara usage lookup failed: {}", e.getMessage());
                usageError = "Apibara did not answer";
            }
        }
        return new AuctionStatus(configured, quotaTracker.latest().orElse(null), usage, usageError, scheduler.isRunning(), runs.findLatestPerParser(),
            currency.lastRate().orElse(null), w8Config.getPriceList(), w8Rates.countByPriceList(w8Config.getPriceList()),
            feedSync.status(), scheduler.nextScheduledRun().orElse(null));
    }

    /**
     * 202 when a background sync was started (also while the automatic schedule is disabled), 409 when one is already
     * running. maxRequests: how many requests this run may spend, 1 … what the period still allows (manualMax, which
     * can exceed today's share); without it, today's allowance. 400 outside that range.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> sync(@AuthenticationPrincipal String adminId,
                                                    @RequestHeader(value = "X-User-Email", required = false) String adminEmail,
                                                    @RequestParam(required = false) Integer maxRequests) {
        if (maxRequests != null) {
            int max = budget.today().manualMax();
            if (maxRequests < 1 || maxRequests > max) {
                return ResponseEntity.badRequest().body(Map.of("error", max < 1
                    ? "Budżet synchronizacji na ten okres jest wykorzystany"
                    : "Liczba zapytań musi być od 1 do " + max));
            }
        }
        if (!scheduler.triggerManualSync(maxRequests)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Sync already running"));
        }
        auditPublisher.publish(new AdminAuditPublisher.Actor(adminId, adminEmail), "AUCTION_SYNC_TRIGGERED",
            "AUCTION_SYNC", null, null, maxRequests == null ? Map.of() : Map.of("maxRequests", maxRequests));
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }
}
