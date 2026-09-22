package com.carmarket.chat.event;

import java.time.Instant;
import java.util.UUID;

/** A buyer sent a message to a seller. Published to Kafka topic {@code chat.message.sent}. */
public record MessageSentEvent(
    UUID messageId,
    UUID conversationId,
    UUID carId,
    UUID senderId,
    UUID recipientId,
    Instant sentAt
) {
}
