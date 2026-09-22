package com.carmarket.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards chat domain events to Kafka once the DB transaction has committed,
 * so consumers never see a message that was rolled back. Kafka problems are
 * logged and never break sending a chat message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    public static final String TOPIC_MESSAGE_SENT = "chat.message.sent";
    public static final String TOPIC_CONVERSATION_READ = "chat.conversation.read";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MessageSentEvent event) {
        send(TOPIC_MESSAGE_SENT, event.conversationId().toString(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ConversationReadEvent event) {
        send(TOPIC_CONVERSATION_READ, event.conversationId().toString(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload);
        } catch (Exception e) {
            log.error("Failed to publish {} for key {}", topic, key, e);
        }
    }
}
