package com.carmarket.car.dto;

/**
 * user.blocked / user.unblocked from auth-service (its own copy lives in AdminUserService's event map;
 * user-service and chat-service have their own consumers). occurredAt is an ISO instant.
 */
public record UserStatusEvent(String userId, String reason, String occurredAt) {
}
