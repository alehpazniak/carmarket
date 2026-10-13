package com.carmarket.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** An account blocked in auth-service; kept here so the WebSocket path can refuse it without a remote call. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blocked_users")
public class BlockedUser {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;
}
