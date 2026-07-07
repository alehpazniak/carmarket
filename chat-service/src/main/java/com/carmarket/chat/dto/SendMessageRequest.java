package com.carmarket.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Sent by the client over STOMP to /app/chat.send
 */
public record SendMessageRequest(
    @NotNull UUID carId,
    @NotNull UUID sellerId,          // who owns the listing (buyer provides it from the car page)
    @NotBlank @Size(max = 2000) String content
) {
}
