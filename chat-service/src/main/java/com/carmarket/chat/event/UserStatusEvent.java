package com.carmarket.chat.event;

/** user.blocked / user.unblocked from auth-service; occurredAt is an ISO instant. */
public record UserStatusEvent(String userId, String reason, String occurredAt) {
}
