package com.carmarket.importorder.dto;

import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.entity.ImportOrderStatus;
import com.carmarket.importorder.entity.LotSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the client sees on /zlecenie/{code}: an explicit allow-list built by hand in
 * {@code ClientViewFactory}, like user-service's PublicUserProfileResponse, so new order fields
 * never leak. It must never contain internal notes, internal event notes, hidden files, the dealer
 * id, access fields, the purchase price or the client's own email/phone.
 */
public record ClientOrderView(
    LotSource source,
    String lotNumber,
    String vin,
    String make,
    String model,
    Integer year,
    LocalDate saleDate,
    String auctionLocation,
    String titleType,
    Boolean titleExportOk,
    Boolean titleRegistrationOk,
    ImportOrderStatus status,
    /** Newest first. */
    List<Event> events,
    Shipping shipping,
    List<File> documents,
    List<File> photos,
    DealerContact dealer,
    /** When the file URLs stop working; refetch the view after that. Null when there are no URLs. */
    Instant urlsExpireAt
) {

    public record Event(ImportOrderStatus status, Instant occurredAt, String note) {
    }

    public record Shipping(
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
        LocalDate customsClearedAt
    ) {
    }

    /** {@code url} is a short-lived presigned link, or null while storage is not configured. */
    public record File(
        UUID id,
        FileKind kind,
        String category,
        String originalName,
        String contentType,
        long sizeBytes,
        String url
    ) {
    }

    public record DealerContact(String name, String phone, String email) {
    }
}
