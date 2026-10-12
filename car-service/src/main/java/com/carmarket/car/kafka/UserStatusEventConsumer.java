package com.carmarket.car.kafka;

import com.carmarket.car.dto.UserStatusEvent;
import com.carmarket.car.service.SellerAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** auth-service → car-service: suspends / restores the listings of a blocked / unblocked seller. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusEventConsumer {

    private final SellerAccountService sellerAccountService;

    @KafkaListener(topics = "user.blocked", groupId = "car-service", containerFactory = "userEventListenerFactory")
    public void handleBlocked(UserStatusEvent event) {
        UUID sellerId = sellerId(event);
        if (sellerId != null) sellerAccountService.suspendListings(sellerId);
    }

    @KafkaListener(topics = "user.unblocked", groupId = "car-service", containerFactory = "userEventListenerFactory")
    public void handleUnblocked(UserStatusEvent event) {
        UUID sellerId = sellerId(event);
        if (sellerId != null) sellerAccountService.restoreListings(sellerId);
    }

    private static UUID sellerId(UserStatusEvent event) {
        try {
            return UUID.fromString(event.userId());
        } catch (RuntimeException e) {
            log.warn("Ignoring user status event without a valid userId: {}", event);
            return null;
        }
    }
}
