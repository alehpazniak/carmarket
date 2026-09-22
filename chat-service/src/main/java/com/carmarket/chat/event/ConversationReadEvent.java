package com.carmarket.chat.event;

import java.time.Instant;
import java.util.UUID;

/** A participant read a conversation. Published to Kafka topic {@code chat.conversation.read}. */
public record ConversationReadEvent(
    UUID conversationId,
    UUID readerId,
    Instant readAt
) {
}
