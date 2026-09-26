package com.carmarket.payment.kafka;

import com.carmarket.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * Publishes payment.completed / payment.refunded so other services (e.g. car-service for
 * listing promotion) can react. Events are sent only after the DB transaction commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    static final String TOPIC_PAYMENT_COMPLETED = "payment.completed";
    static final String TOPIC_PAYMENT_REFUNDED = "payment.refunded";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record PaymentEvent(
        String paymentId,
        String userId,
        String productCode,
        String referenceId,
        int amount,
        String currency,
        Long p24OrderId,
        Instant occurredAt
    ) {
    }

    public void publishCompleted(Payment payment) {
        sendAfterCommit(TOPIC_PAYMENT_COMPLETED, toEvent(payment, payment.getPaidAt()));
    }

    public void publishRefunded(Payment payment) {
        sendAfterCommit(TOPIC_PAYMENT_REFUNDED, toEvent(payment, payment.getRefundedAt()));
    }

    private void sendAfterCommit(String topic, PaymentEvent event) {
        Runnable send = () -> {
            kafkaTemplate.send(topic, event.paymentId(), event);
            log.info("Published {} for paymentId: {}", topic, event.paymentId());
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private PaymentEvent toEvent(Payment p, Instant occurredAt) {
        return new PaymentEvent(
            p.getId().toString(),
            p.getUserId().toString(),
            p.getProductCode(),
            p.getReferenceId(),
            p.getAmount(),
            p.getCurrency(),
            p.getP24OrderId(),
            occurredAt
        );
    }
}
