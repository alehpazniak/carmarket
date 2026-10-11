package com.carmarket.importorder.dto;

import com.carmarket.importorder.entity.AccessEvent;
import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.entity.ImportOrderStatus;
import com.carmarket.importorder.entity.LotSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the dealer API. The client page has its own {@link ClientOrderView}. */
public final class ImportOrderDtos {

    private ImportOrderDtos() {
    }

    // ── requests ──

    public record AddEventRequest(
        @NotNull ImportOrderStatus status,
        @NotNull Instant occurredAt,
        @Size(max = 2000) String clientNote,
        @Size(max = 5000) String internalNote
    ) {
    }

    public record FilePatchRequest(Boolean clientVisible, String category) {
    }

    public record AccessEnabledRequest(@NotNull Boolean enabled) {
    }

    public record ResetAccessRequest(boolean rotateLink) {
    }

    public record ClientAccessRequest(@NotNull @Size(max = 100) String password) {
    }

    // ── responses ──

    public record OrderSummary(
        UUID id,
        ImportOrderStatus status,
        LotSource source,
        String lotNumber,
        String vin,
        String make,
        String model,
        Integer year,
        String clientName,
        LocalDate eta,
        String containerNumber,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt
    ) {
    }

    public record OrderDetail(
        UUID id,
        UUID dealerId,
        ImportOrderStatus status,
        LotSource source,
        String lotNumber,
        String vin,
        String make,
        String model,
        Integer year,
        LocalDate saleDate,
        BigDecimal purchasePriceUsd,
        String auctionLocation,
        String titleType,
        Boolean titleExportOk,
        Boolean titleRegistrationOk,
        Map<String, Object> auctionSnapshot,
        LocalDate paymentDeadline,
        LocalDate titleReceivedAt,
        String portOfLoading,
        String shippingLine,
        String containerNumber,
        String bookingNumber,
        String billOfLading,
        String vesselName,
        LocalDate etd,
        LocalDate eta,
        String destinationPort,
        LocalDate customsClearedAt,
        String clientName,
        String clientEmail,
        String clientPhone,
        boolean notifyClient,
        String dealerContactName,
        String dealerContactPhone,
        String dealerContactEmail,
        String internalNotes,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        AccessInfo access,
        List<EventView> events,
        List<FileView> files,
        /** E.g. a VIN whose North-American check digit doesn't match. Not errors: the order is saved. */
        List<String> warnings,
        boolean storageEnabled
    ) {
    }

    public record AccessInfo(
        String clientLink,
        boolean enabled,
        int accessVersion,
        int failedAttempts,
        Instant lockedUntil
    ) {
    }

    public record EventView(
        UUID id,
        ImportOrderStatus status,
        Instant occurredAt,
        String clientNote,
        String internalNote,
        UUID createdBy,
        Instant createdAt
    ) {
    }

    public record FileView(
        UUID id,
        FileKind kind,
        String category,
        String originalName,
        String contentType,
        long sizeBytes,
        boolean clientVisible,
        Instant createdAt
    ) {
    }

    /** Returned once on create / reset; only the hash is stored, so the password can't be shown again. */
    public record ClientCredentials(String clientLink, String password) {
    }

    public record CreatedOrder(OrderDetail order, String clientLink, String password) {
    }

    public record PresignedUrl(String url, Instant expiresAt) {
    }

    public record AccessLogView(AccessEvent event, Instant at, String ipPrefix, String userAgent) {
    }

    public record ClientAccessGranted(String token, Instant expiresAt) {
    }
}
