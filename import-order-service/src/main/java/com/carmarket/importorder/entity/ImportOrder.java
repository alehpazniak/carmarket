package com.carmarket.importorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * An import order owned by one dealer. The client reaches it with /zlecenie/{publicCode} plus a
 * password; only the BCrypt hash of that password is stored.
 */
@Entity
@Table(name = "import_orders")
@Getter
@Setter
@NoArgsConstructor
public class ImportOrder {

    @Id
    private UUID id;

    @Column(name = "dealer_id", nullable = false)
    private UUID dealerId;

    /** Status of the latest event; null while the timeline is empty. Kept in sync by the service. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ImportOrderStatus status;

    // ── client access ──
    @Column(name = "public_code", nullable = false, unique = true, length = 32)
    private String publicCode;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "access_version", nullable = false)
    private int accessVersion = 1;

    @Column(name = "client_access_enabled", nullable = false)
    private boolean clientAccessEnabled = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // ── lot ──
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LotSource source;

    @Column(name = "lot_number", length = 30)
    private String lotNumber;

    @Column(length = 17)
    private String vin;

    @Column(length = 60)
    private String make;

    @Column(length = 80)
    private String model;

    private Integer year;

    @Column(name = "sale_date")
    private LocalDate saleDate;

    @Column(name = "purchase_price_usd", precision = 12, scale = 2)
    private BigDecimal purchasePriceUsd;

    @Column(name = "auction_location", length = 200)
    private String auctionLocation;

    @Column(name = "title_type", length = 120)
    private String titleType;

    @Column(name = "title_export_ok")
    private Boolean titleExportOk;

    @Column(name = "title_registration_ok")
    private Boolean titleRegistrationOk;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auction_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> auctionSnapshot;

    // ── shipping ──
    @Column(name = "payment_deadline")
    private LocalDate paymentDeadline;

    @Column(name = "title_received_at")
    private LocalDate titleReceivedAt;

    @Column(name = "port_of_loading", length = 120)
    private String portOfLoading;

    @Column(name = "shipping_line", length = 120)
    private String shippingLine;

    @Column(name = "container_number", length = 11)
    private String containerNumber;

    @Column(name = "booking_number", length = 60)
    private String bookingNumber;

    @Column(name = "bill_of_lading", length = 60)
    private String billOfLading;

    @Column(name = "vessel_name", length = 120)
    private String vesselName;

    private LocalDate etd;

    private LocalDate eta;

    @Column(name = "destination_port", length = 120)
    private String destinationPort;

    @Column(name = "customs_cleared_at")
    private LocalDate customsClearedAt;

    // ── people ──
    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "client_email")
    private String clientEmail;

    @Column(name = "client_phone", length = 40)
    private String clientPhone;

    @Column(name = "notify_client", nullable = false)
    private boolean notifyClient;

    @Column(name = "dealer_contact_name", length = 200)
    private String dealerContactName;

    @Column(name = "dealer_contact_phone", length = 40)
    private String dealerContactPhone;

    @Column(name = "dealer_contact_email")
    private String dealerContactEmail;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** True while the client page may be opened: access on and the order not archived. */
    public boolean isClientAccessOpen() {
        return clientAccessEnabled && archivedAt == null;
    }
}
