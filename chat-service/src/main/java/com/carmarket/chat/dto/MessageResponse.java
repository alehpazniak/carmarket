package com.carmarket.chat.dto;

import com.carmarket.chat.entity.Message;

import java.time.Instant;
import java.util.UUID;

/** carId comes from the message's conversation, so clients can tell which listing a realtime message belongs to. */
public record MessageResponse(
    UUID id,
    UUID conversationId,
    UUID carId,
    UUID senderId,
    String content,
    Instant createdAt,
    Instant readAt
) {
    public static MessageResponse from(Message m, UUID carId) {
        return new MessageResponse(
            m.getId(), m.getConversationId(), carId, m.getSenderId(),
            m.getContent(), m.getCreatedAt(), m.getReadAt());
    }
}
