package com.carmarket.car.kafka;

import com.carmarket.car.dto.PaymentEvent;
import com.carmarket.car.service.ListingPromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** payment-service → car-service: applies/reverts paid listing promotions. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ListingPromotionService promotionService;

    @KafkaListener(topics = "payment.completed", groupId = "car-service")
    public void handleCompleted(PaymentEvent event) {
        log.info("Received payment.completed for paymentId: {}", event.paymentId());
        promotionService.applyCompleted(event);
    }

    @KafkaListener(topics = "payment.refunded", groupId = "car-service")
    public void handleRefunded(PaymentEvent event) {
        log.info("Received payment.refunded for paymentId: {}", event.paymentId());
        promotionService.applyRefunded(event);
    }
}
