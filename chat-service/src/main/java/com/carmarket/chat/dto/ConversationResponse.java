package com.carmarket.chat.dto;

import com.carmarket.chat.entity.Conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    UUID carId,
    UUID buyerId,
    UUID sellerId,
    Instant lastMessageAt
) {
    public static ConversationResponse from(Conversation c) {
        return new ConversationResponse(
            c.getId(), c.getCarId(), c.getBuyerId(), c.getSellerId(), c.getLastMessageAt());
    }
}
