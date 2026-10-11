package com.carmarket.importorder.controller;

import com.carmarket.importorder.dto.ClientOrderView;
import com.carmarket.importorder.dto.ImportOrderDtos.AccessEnabledRequest;
import com.carmarket.importorder.dto.ImportOrderDtos.AccessLogView;
import com.carmarket.importorder.dto.ImportOrderDtos.AddEventRequest;
import com.carmarket.importorder.dto.ImportOrderDtos.ClientCredentials;
import com.carmarket.importorder.dto.ImportOrderDtos.CreatedOrder;
import com.carmarket.importorder.dto.ImportOrderDtos.FilePatchRequest;
import com.carmarket.importorder.dto.ImportOrderDtos.FileView;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderDetail;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderSummary;
import com.carmarket.importorder.dto.ImportOrderDtos.PresignedUrl;
import com.carmarket.importorder.dto.ImportOrderDtos.ResetAccessRequest;
import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.entity.ImportOrderStatus;
import com.carmarket.importorder.security.Actor;
import com.carmarket.importorder.service.ImportOrderService;
import com.carmarket.importorder.service.OrderFileService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Dealer cabinet API (DEALER or ADMIN, see SecurityConfig). Everything is scoped to the caller's
 * own orders; admins can also read other dealers' orders.
 *
 * POST   /import-orders                         → create; returns the client link + password once
 * GET    /import-orders?status=&q=&archived=&page=&size=
 * GET    /import-orders/{id}
 * PATCH  /import-orders/{id}                    → partial update (absent = unchanged, null = clear)
 * DELETE /import-orders/{id}                    → hard delete, rows + S3 objects
 * POST   /import-orders/{id}/archive | /unarchive
 * POST   /import-orders/{id}/events             DELETE /import-orders/{id}/events/{eventId}
 * POST   /import-orders/{id}/files (multipart)  PATCH | DELETE /import-orders/{id}/files/{fileId}
 * GET    /import-orders/{id}/files/{fileId}/url → presigned download URL (5 min)
 * POST   /import-orders/{id}/access/reset       → new password once (optional rotateLink)
 * PATCH  /import-orders/{id}/access             → {enabled}
 * GET    /import-orders/{id}/access-log
 * GET    /import-orders/{id}/client-preview     → exactly what the client sees
 */
@RestController
@RequestMapping("/import-orders")
@RequiredArgsConstructor
public class ImportOrderController {

    private final ImportOrderService service;
    private final OrderFileService fileService;

    @PostMapping
    public ResponseEntity<CreatedOrder> create(Authentication auth, @RequestBody JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(Actor.from(auth), body));
    }

    @GetMapping
    public Page<OrderSummary> list(Authentication auth,
                                   @RequestParam(required = false) ImportOrderStatus status,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "false") boolean archived,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(Actor.from(auth), status, q, archived, page, size);
    }

    @GetMapping("/{id}")
    public OrderDetail get(Authentication auth, @PathVariable UUID id) {
        return service.get(Actor.from(auth), id);
    }

    @PatchMapping("/{id}")
    public OrderDetail update(Authentication auth, @PathVariable UUID id, @RequestBody JsonNode body) {
        return service.update(Actor.from(auth), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        service.delete(Actor.from(auth), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    public OrderDetail archive(Authentication auth, @PathVariable UUID id) {
        return service.setArchived(Actor.from(auth), id, true);
    }

    @PostMapping("/{id}/unarchive")
    public OrderDetail unarchive(Authentication auth, @PathVariable UUID id) {
        return service.setArchived(Actor.from(auth), id, false);
    }

    // ── status timeline ──

    @PostMapping("/{id}/events")
    public OrderDetail addEvent(Authentication auth, @PathVariable UUID id, @Valid @RequestBody AddEventRequest request) {
        return service.addEvent(Actor.from(auth), id, request);
    }

    @DeleteMapping("/{id}/events/{eventId}")
    public OrderDetail deleteEvent(Authentication auth, @PathVariable UUID id, @PathVariable UUID eventId) {
        return service.deleteEvent(Actor.from(auth), id, eventId);
    }

    // ── files ──

    @PostMapping(value = "/{id}/files", consumes = "multipart/form-data")
    public ResponseEntity<List<FileView>> upload(Authentication auth, @PathVariable UUID id,
                                                 @RequestPart("files") List<MultipartFile> files,
                                                 @RequestParam FileKind kind,
                                                 @RequestParam String category,
                                                 @RequestParam(defaultValue = "false") boolean clientVisible) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fileService.upload(Actor.from(auth), id, files, kind, category, clientVisible));
    }

    @PatchMapping("/{id}/files/{fileId}")
    public FileView patchFile(Authentication auth, @PathVariable UUID id, @PathVariable UUID fileId,
                              @RequestBody FilePatchRequest request) {
        return fileService.patch(Actor.from(auth), id, fileId, request);
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(Authentication auth, @PathVariable UUID id, @PathVariable UUID fileId) {
        fileService.delete(Actor.from(auth), id, fileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/files/{fileId}/url")
    public PresignedUrl fileUrl(Authentication auth, @PathVariable UUID id, @PathVariable UUID fileId) {
        return fileService.downloadUrl(Actor.from(auth), id, fileId);
    }

    // ── client access ──

    @PostMapping("/{id}/access/reset")
    public ClientCredentials resetAccess(Authentication auth, @PathVariable UUID id,
                                         @RequestBody(required = false) ResetAccessRequest request) {
        return service.resetAccess(Actor.from(auth), id, request != null && request.rotateLink());
    }

    @PatchMapping("/{id}/access")
    public OrderDetail setAccess(Authentication auth, @PathVariable UUID id,
                                 @Valid @RequestBody AccessEnabledRequest request) {
        return service.setAccessEnabled(Actor.from(auth), id, request.enabled());
    }

    @GetMapping("/{id}/access-log")
    public List<AccessLogView> accessLog(Authentication auth, @PathVariable UUID id) {
        return service.accessLog(Actor.from(auth), id);
    }

    @GetMapping("/{id}/client-preview")
    public ClientOrderView clientPreview(Authentication auth, @PathVariable UUID id) {
        return service.clientPreview(Actor.from(auth), id);
    }
}
