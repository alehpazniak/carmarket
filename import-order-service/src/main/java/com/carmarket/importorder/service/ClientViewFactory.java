package com.carmarket.importorder.service;

import com.carmarket.importorder.dto.ClientOrderView;
import com.carmarket.importorder.dto.ImportOrderDtos.PresignedUrl;
import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.ImportOrderEvent;
import com.carmarket.importorder.entity.ImportOrderFile;
import com.carmarket.importorder.repository.ImportOrderEventRepository;
import com.carmarket.importorder.repository.ImportOrderFileRepository;
import com.carmarket.importorder.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link ClientOrderView} field by field from an allow-list. Used by the client page and by
 * the dealer's "preview as client", so both always show exactly the same thing.
 */
@Component
@RequiredArgsConstructor
public class ClientViewFactory {

    private final ImportOrderEventRepository events;
    private final ImportOrderFileRepository files;
    private final FileStorage storage;

    public ClientOrderView build(ImportOrder order) {
        List<ClientOrderView.Event> timeline = events.findByOrderIdOrderByOccurredAtDescCreatedAtDesc(order.getId())
            .stream()
            .map(ClientViewFactory::event)
            .toList();

        List<ClientOrderView.File> documents = new ArrayList<>();
        List<ClientOrderView.File> photos = new ArrayList<>();
        Instant urlsExpireAt = null;
        for (ImportOrderFile file : files.findByOrderIdAndClientVisibleTrueOrderByCreatedAtAsc(order.getId())) {
            String url = null;
            if (storage.isEnabled()) {
                PresignedUrl presigned = storage.presignGet(file.getS3Key(), file.getOriginalName(),
                    file.getContentType(), file.getKind() == FileKind.DOCUMENT);
                url = presigned.url();
                if (urlsExpireAt == null || presigned.expiresAt().isBefore(urlsExpireAt)) {
                    urlsExpireAt = presigned.expiresAt();
                }
            }
            ClientOrderView.File view = new ClientOrderView.File(file.getId(), file.getKind(), file.getCategory(),
                file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.isUploadedByClient(), url);
            (file.getKind() == FileKind.DOCUMENT ? documents : photos).add(view);
        }

        return new ClientOrderView(
            order.getSource(),
            order.getLotNumber(),
            order.getVin(),
            order.getMake(),
            order.getModel(),
            order.getYear(),
            order.getSaleDate(),
            order.getAuctionLocation(),
            order.getTitleType(),
            order.getTitleExportOk(),
            order.getTitleRegistrationOk(),
            order.getStatus(),
            timeline,
            new ClientOrderView.Shipping(
                order.getPaymentDeadline(),
                order.getTitleReceivedAt(),
                order.getPortOfLoading(),
                order.getShippingLine(),
                order.getContainerNumber(),
                order.getBookingNumber(),
                order.getBillOfLading(),
                order.getVesselName(),
                order.getEtd(),
                order.getEta(),
                order.getDestinationPort(),
                order.getCustomsClearedAt()),
            documents,
            photos,
            new ClientOrderView.DealerContact(
                order.getDealerContactName(),
                order.getDealerContactPhone(),
                order.getDealerContactEmail()),
            urlsExpireAt
        );
    }

    /** Only the client note: the internal note never leaves the dealer API. */
    private static ClientOrderView.Event event(ImportOrderEvent e) {
        return new ClientOrderView.Event(e.getStatus(), e.getOccurredAt(), e.getClientNote());
    }
}
