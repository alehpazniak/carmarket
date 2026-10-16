package com.carmarket.importorder.service;

import com.carmarket.importorder.config.ImportOrderProperties;
import com.carmarket.importorder.dto.ImportOrderDtos.AccessInfo;
import com.carmarket.importorder.dto.ImportOrderDtos.AccessLogView;
import com.carmarket.importorder.dto.ImportOrderDtos.EventView;
import com.carmarket.importorder.dto.ImportOrderDtos.FileView;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderDetail;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderSummary;
import com.carmarket.importorder.entity.AccessLogEntry;
import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.entity.PhotoStage;
import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.ImportOrderEvent;
import com.carmarket.importorder.entity.ImportOrderFile;
import com.carmarket.importorder.repository.ImportOrderEventRepository;
import com.carmarket.importorder.repository.ImportOrderFileRepository;
import com.carmarket.importorder.storage.FileStorage;
import com.carmarket.importorder.validation.VinValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Entity → dealer API responses. */
@Component
@RequiredArgsConstructor
public class OrderViews {

    private final ImportOrderProperties properties;
    private final ImportOrderEventRepository events;
    private final ImportOrderFileRepository files;
    private final FileStorage storage;

    /**
     * List rows with a thumbnail: the first AUCTION-stage photo, else the first photo, as a 5-minute
     * presigned URL (one query for the whole page; presigning is local, no S3 call).
     */
    public Page<OrderSummary> summaries(Page<ImportOrder> page) {
        Map<UUID, ImportOrderFile> thumbs = new HashMap<>();
        if (storage.isEnabled() && page.hasContent()) {
            List<UUID> ids = page.getContent().stream().map(ImportOrder::getId).toList();
            for (ImportOrderFile f : files.findByOrderIdInAndKindOrderByCreatedAtAsc(ids, FileKind.PHOTO)) {
                ImportOrderFile current = thumbs.get(f.getOrderId());
                boolean auction = PhotoStage.AUCTION.name().equals(f.getCategory());
                if (current == null || (auction && !PhotoStage.AUCTION.name().equals(current.getCategory()))) {
                    thumbs.put(f.getOrderId(), f);
                }
            }
        }
        return page.map(o -> {
            ImportOrderFile thumb = thumbs.get(o.getId());
            String url = thumb == null ? null
                : storage.presignGet(thumb.getS3Key(), thumb.getOriginalName(), thumb.getContentType(), false).url();
            return summary(o, url);
        });
    }

    public OrderSummary summary(ImportOrder o, String thumbnailUrl) {
        return new OrderSummary(o.getId(), o.getDealerId(), o.getStatus(), o.getSource(), o.getLotNumber(), o.getVin(), o.getMake(),
            o.getModel(), o.getYear(), o.getClientName(), o.getEta(), o.getContainerNumber(), o.getAuctionUrl(),
            thumbnailUrl, AuctionSnapshots.firstPhotoUrl(o.getAuctionSnapshot()), o.getCreatedAt(),
            o.getUpdatedAt(), o.getArchivedAt());
    }

    public OrderDetail detail(ImportOrder o) {
        List<EventView> timeline = events.findByOrderIdOrderByOccurredAtDescCreatedAtDesc(o.getId()).stream()
            .map(OrderViews::event)
            .toList();
        List<FileView> fileViews = files.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream()
            .map(OrderViews::file)
            .toList();
        List<String> warnings = new ArrayList<>();
        if (o.getVin() != null && VinValidator.isValidFormat(o.getVin())) {
            VinValidator.checkDigitWarning(o.getVin()).ifPresent(warnings::add);
        }
        return new OrderDetail(
            o.getId(), o.getDealerId(), o.getStatus(), o.getSource(), o.getLotNumber(), o.getVin(), o.getMake(),
            o.getModel(), o.getYear(), o.getSaleDate(), o.getPurchasePriceUsd(), o.getAuctionLocation(),
            o.getAuctionUrl(), o.getTitleType(), o.getTitleExportOk(), o.getTitleRegistrationOk(), o.getAuctionSnapshot(),
            o.getPaymentDeadline(), o.getTitleReceivedAt(), o.getPortOfLoading(), o.getShippingLine(),
            o.getContainerNumber(), o.getBookingNumber(), o.getBillOfLading(), o.getVesselName(), o.getEtd(),
            o.getEta(), o.getDestinationPort(), o.getCustomsClearedAt(), o.getClientName(), o.getClientEmail(),
            o.getClientPhone(), o.isNotifyClient(), o.getDealerContactName(), o.getDealerContactPhone(),
            o.getDealerContactEmail(), o.getInternalNotes(), o.getCreatedAt(), o.getUpdatedAt(), o.getArchivedAt(),
            new AccessInfo(properties.clientLink(o.getPublicCode()), o.isClientAccessEnabled(), o.getAccessVersion(),
                o.getFailedAttempts(), o.getLockedUntil()),
            timeline, fileViews, warnings, storage.isEnabled());
    }

    public static EventView event(ImportOrderEvent e) {
        return new EventView(e.getId(), e.getStatus(), e.getOccurredAt(), e.getClientNote(), e.getInternalNote(),
            e.getCreatedBy(), e.getCreatedAt());
    }

    public static FileView file(ImportOrderFile f) {
        return new FileView(f.getId(), f.getKind(), f.getCategory(), f.getOriginalName(), f.getContentType(),
            f.getSizeBytes(), f.isClientVisible(), f.getCreatedAt());
    }

    public static AccessLogView accessLog(AccessLogEntry e) {
        return new AccessLogView(e.getEvent(), e.getAt(), e.getIpPrefix(), e.getUserAgent());
    }
}
