package com.carmarket.importorder.service;

import com.carmarket.importorder.config.ImportOrderProperties;
import com.carmarket.importorder.dto.ImportOrderDtos.AccessInfo;
import com.carmarket.importorder.dto.ImportOrderDtos.AccessLogView;
import com.carmarket.importorder.dto.ImportOrderDtos.EventView;
import com.carmarket.importorder.dto.ImportOrderDtos.FileView;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderDetail;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderSummary;
import com.carmarket.importorder.entity.AccessLogEntry;
import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.ImportOrderEvent;
import com.carmarket.importorder.entity.ImportOrderFile;
import com.carmarket.importorder.repository.ImportOrderEventRepository;
import com.carmarket.importorder.repository.ImportOrderFileRepository;
import com.carmarket.importorder.storage.FileStorage;
import com.carmarket.importorder.validation.VinValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Entity → dealer API responses. */
@Component
@RequiredArgsConstructor
public class OrderViews {

    private final ImportOrderProperties properties;
    private final ImportOrderEventRepository events;
    private final ImportOrderFileRepository files;
    private final FileStorage storage;

    public OrderSummary summary(ImportOrder o) {
        return new OrderSummary(o.getId(), o.getStatus(), o.getSource(), o.getLotNumber(), o.getVin(), o.getMake(),
            o.getModel(), o.getYear(), o.getClientName(), o.getEta(), o.getContainerNumber(), o.getCreatedAt(),
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
            o.getTitleType(), o.getTitleExportOk(), o.getTitleRegistrationOk(), o.getAuctionSnapshot(),
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
