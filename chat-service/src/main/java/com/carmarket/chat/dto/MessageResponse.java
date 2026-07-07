package com.carmarket.chat.dto;

import com.carmarket.chat.entity.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID conversationId,
    UUID senderId,
    String content,
    Instant createdAt,
    Instant readAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
            m.getId(), m.getConversationId(), m.getSenderId(),
            m.getContent(), m.getCreatedAt(), m.getReadAt());
    }
}
