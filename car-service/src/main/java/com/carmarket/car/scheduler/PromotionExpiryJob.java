package com.carmarket.car.scheduler;

import com.carmarket.car.service.ListingPromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Clears expired promotedUntil/highlightedUntil and re-publishes car.updated for search-service. */
@Component
@RequiredArgsConstructor
public class PromotionExpiryJob {

    private final ListingPromotionService promotionService;

    @Scheduled(fixedDelayString = "${promotion.expiry.fixed-delay-ms:600000}",
        initialDelayString = "${promotion.expiry.initial-delay-ms:60000}")
    public void expirePromotions() {
        promotionService.expirePromotions();
    }
}
