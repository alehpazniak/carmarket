package com.carmarket.importorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_order_access_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogEntry {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessEvent event;

    @Column(nullable = false)
    private Instant at;

    /** IPv4 /24 or IPv6 /48 only. */
    @Column(name = "ip_prefix", length = 50)
    private String ipPrefix;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    @Column(name = "token_id", length = 36)
    private String tokenId;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (at == null) at = Instant.now();
    }
}
