package com.carmarket.importorder.kafka;

import com.carmarket.importorder.config.ImportOrderProperties;
import com.carmarket.importorder.entity.ImportOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Publishes import-order.client-update so notification-service can email the client that there
 * is something new on their page. Sent only after the DB transaction commits, and only when the
 * dealer turned notifications on, the client has an email and the client page is open. The event
 * never contains the page password (only its hash exists anyway).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportOrderEventProducer {

    static final String TOPIC_CLIENT_UPDATE = "import-order.client-update";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ImportOrderProperties properties;

    public enum Reason { STATUS, DOCUMENT }

    public record ClientUpdateEvent(
        String orderId,
        String clientEmail,
        String clientName,
        String dealerName,
        String carTitle,
        String link,
        Reason reason,
        Instant occurredAt
    ) {
    }

    /** @return whether an event will be sent (after commit) */
    public boolean publishClientUpdate(ImportOrder order, Reason reason) {
        if (!order.isNotifyClient() || order.getClientEmail() == null || order.getClientEmail().isBlank()
            || !order.isClientAccessOpen()) {
            return false;
        }
        // Built now, so it reflects the state being committed
        ClientUpdateEvent event = new ClientUpdateEvent(
            order.getId().toString(),
            order.getClientEmail(),
            order.getClientName(),
            order.getDealerContactName(),
            carTitle(order),
            properties.clientLink(order.getPublicCode()),
            reason,
            Instant.now());
        sendAfterCommit(event);
        return true;
    }

    private void sendAfterCommit(ClientUpdateEvent event) {
        Runnable send = () -> {
            kafkaTemplate.send(TOPIC_CLIENT_UPDATE, event.orderId(), event);
            log.info("Published {} ({}) for import order {}", TOPIC_CLIENT_UPDATE, event.reason(), event.orderId());
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

    static String carTitle(ImportOrder o) {
        String title = Stream.of(o.getMake(), o.getModel(), o.getYear() == null ? null : o.getYear().toString())
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.joining(" "));
        return title.isEmpty() ? null : title;
    }
}
