package com.carmarket.importorder.service;

import com.carmarket.importorder.config.ImportOrderProperties;
import com.carmarket.importorder.dto.ClientOrderView;
import com.carmarket.importorder.dto.ImportOrderDtos.AccessLogView;
import com.carmarket.importorder.dto.ImportOrderDtos.AddEventRequest;
import com.carmarket.importorder.dto.ImportOrderDtos.ClientCredentials;
import com.carmarket.importorder.dto.ImportOrderDtos.CreatedOrder;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderDetail;
import com.carmarket.importorder.dto.ImportOrderDtos.OrderSummary;
import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.ImportOrderEvent;
import com.carmarket.importorder.entity.ImportOrderFile;
import com.carmarket.importorder.entity.ImportOrderStatus;
import com.carmarket.importorder.exception.ApiException;
import com.carmarket.importorder.kafka.ImportOrderEventProducer;
import com.carmarket.importorder.repository.AccessLogRepository;
import com.carmarket.importorder.repository.ImportOrderEventRepository;
import com.carmarket.importorder.repository.ImportOrderFileRepository;
import com.carmarket.importorder.repository.ImportOrderRepository;
import com.carmarket.importorder.security.Actor;
import com.carmarket.importorder.storage.FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Dealer side of import orders: CRUD, status timeline and client access management. */
@Slf4j
@Service
public class ImportOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int ACCESS_LOG_LIMIT = 200;
    /** A status date may be a little ahead of the server clock (time zones), but not really in the future. */
    private static final Duration MAX_FUTURE_EVENT = Duration.ofDays(1);

    private final ImportOrderRepository orders;
    private final ImportOrderEventRepository events;
    private final ImportOrderFileRepository files;
    private final AccessLogRepository accessLog;
    private final OrderLookup lookup;
    private final OrderViews views;
    private final ClientViewFactory clientViews;
    private final ClientCredentialsGenerator credentials;
    private final BCryptPasswordEncoder passwordEncoder;
    private final FileStorage storage;
    private final ImportOrderProperties properties;
    private final OrderFields fields;
    private final ImportOrderEventProducer eventProducer;

    public ImportOrderService(ImportOrderRepository orders, ImportOrderEventRepository events,
                              ImportOrderFileRepository files, AccessLogRepository accessLog, OrderLookup lookup,
                              OrderViews views, ClientViewFactory clientViews, ClientCredentialsGenerator credentials,
                              BCryptPasswordEncoder passwordEncoder, FileStorage storage,
                              ImportOrderProperties properties, ObjectMapper objectMapper,
                              ImportOrderEventProducer eventProducer) {
        this.orders = orders;
        this.events = events;
        this.files = files;
        this.accessLog = accessLog;
        this.lookup = lookup;
        this.views = views;
        this.clientViews = clientViews;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
        this.properties = properties;
        this.fields = new OrderFields(objectMapper);
        this.eventProducer = eventProducer;
    }

    // ── orders ──

    /**
     * Creates the order and returns the client link and password once. Only the BCrypt hash is
     * stored and the password is never logged, so it can't be shown again (only reset).
     */
    @Transactional
    public CreatedOrder create(Actor actor, JsonNode body) {
        ImportOrder order = new ImportOrder();
        order.setDealerId(actor.userId());
        fields.apply(order, body, true);
        if (order.getSource() == null) throw ApiException.badRequest("source is required");
        checkLotIsNew(order);

        String password = credentials.newPassword();
        order.setPublicCode(newUniqueCode());
        order.setPasswordHash(hash(password));
        orders.saveAndFlush(order);
        log.info("Import order {} created by dealer {}", order.getId(), actor.userId());

        return new CreatedOrder(views.detail(order), properties.clientLink(order.getPublicCode()), password);
    }

    /** {@code dealerId} narrows an admin's list to one dealer; for dealers it is ignored (always their own). */
    @Transactional(readOnly = true)
    public Page<OrderSummary> list(Actor actor, ImportOrderStatus status, String q, boolean archived, UUID dealerId,
                                   int page, int size) {
        Specification<ImportOrder> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (!actor.admin()) where.add(cb.equal(root.get("dealerId"), actor.userId()));
            else if (dealerId != null) where.add(cb.equal(root.get("dealerId"), dealerId));
            if (status != null) where.add(cb.equal(root.get("status"), status));
            where.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (q != null && !q.isBlank()) {
                String like = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(
                    cb.like(cb.lower(root.get("vin")), like, '\\'),
                    cb.like(cb.lower(root.get("lotNumber")), like, '\\'),
                    cb.like(cb.lower(root.get("clientName")), like, '\\'),
                    cb.like(cb.lower(root.get("make")), like, '\\'),
                    cb.like(cb.lower(root.get("model")), like, '\\')));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return views.summaries(orders.findAll(spec, pageable));
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Actor actor, UUID orderId) {
        return views.detail(lookup.forRead(actor, orderId));
    }

    @Transactional
    public OrderDetail update(Actor actor, UUID orderId, JsonNode body) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        fields.apply(order, body, false);
        checkLotIsNew(order);
        orders.saveAndFlush(order);
        return views.detail(order);
    }

    @Transactional
    public OrderDetail setArchived(Actor actor, UUID orderId, boolean archived) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        if (archived && order.getArchivedAt() == null) order.setArchivedAt(Instant.now());
        if (!archived) order.setArchivedAt(null);
        return views.detail(order);
    }

    /**
     * Hard delete: S3 objects first, then the rows (events, files and access log cascade). If S3
     * fails nothing is deleted and the dealer can retry; if the database fails afterwards the order
     * is still there and a retry finishes the job.
     */
    @Transactional
    public void delete(Actor actor, UUID orderId) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        List<String> keys = files.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
            .map(ImportOrderFile::getS3Key)
            .toList();
        if (storage.isEnabled()) {
            storage.deleteOrder(orderId, keys);
        } else if (!keys.isEmpty()) {
            throw ApiException.storageNotConfigured();
        }
        orders.delete(order);
        log.info("Import order {} deleted by dealer {} ({} files)", orderId, actor.userId(), keys.size());
    }

    // ── status timeline ──

    @Transactional
    public OrderDetail addEvent(Actor actor, UUID orderId, AddEventRequest request) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        ImportOrderStatus before = order.getStatus();
        if (request.occurredAt().isAfter(Instant.now().plus(MAX_FUTURE_EVENT))) {
            throw ApiException.badRequest("occurredAt can't be in the future");
        }
        events.save(ImportOrderEvent.builder()
            .orderId(orderId)
            .status(request.status())
            .occurredAt(request.occurredAt())
            .clientNote(blankToNull(request.clientNote()))
            .internalNote(blankToNull(request.internalNote()))
            .createdBy(actor.userId())
            .build());
        refreshStatus(order);
        // The client hears about a note meant for them, or any change of the current status
        if (blankToNull(request.clientNote()) != null || order.getStatus() != before) {
            eventProducer.publishClientUpdate(order, ImportOrderEventProducer.Reason.STATUS);
        }
        return views.detail(order);
    }

    @Transactional
    public OrderDetail deleteEvent(Actor actor, UUID orderId, UUID eventId) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        ImportOrderEvent event = events.findByIdAndOrderId(eventId, orderId).orElseThrow(ApiException::notFound);
        ImportOrderStatus before = order.getStatus();
        events.delete(event);
        refreshStatus(order);
        if (order.getStatus() != before) {
            eventProducer.publishClientUpdate(order, ImportOrderEventProducer.Reason.STATUS);
        }
        return views.detail(order);
    }

    /** Current status = the event with the latest occurred_at (ties: the one added last). */
    private void refreshStatus(ImportOrder order) {
        events.flush();
        order.setStatus(events.findFirstByOrderIdOrderByOccurredAtDescCreatedAtDesc(order.getId())
            .map(ImportOrderEvent::getStatus)
            .orElse(null));
        // Touch the order even if the status didn't change, so the list shows the latest activity
        order.setUpdatedAt(Instant.now());
    }

    // ── client access ──

    /**
     * New one-time password; bumping access_version invalidates every client token already issued.
     * With rotateLink the old link stops working too.
     */
    @Transactional
    public ClientCredentials resetAccess(Actor actor, UUID orderId, boolean rotateLink) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        String password = credentials.newPassword();
        order.setPasswordHash(hash(password));
        order.setAccessVersion(order.getAccessVersion() + 1);
        order.setFailedAttempts(0);
        order.setLockedUntil(null);
        if (rotateLink) order.setPublicCode(newUniqueCode());
        log.info("Client access of import order {} reset by dealer {} (new link: {})", orderId, actor.userId(), rotateLink);
        return new ClientCredentials(properties.clientLink(order.getPublicCode()), password);
    }

    @Transactional
    public OrderDetail setAccessEnabled(Actor actor, UUID orderId, boolean enabled) {
        ImportOrder order = lookup.forWrite(actor, orderId);
        order.setClientAccessEnabled(enabled);
        return views.detail(order);
    }

    @Transactional(readOnly = true)
    public List<AccessLogView> accessLog(Actor actor, UUID orderId) {
        lookup.forRead(actor, orderId);
        return accessLog.findByOrderIdOrderByAtDesc(orderId, PageRequest.of(0, ACCESS_LOG_LIMIT)).stream()
            .map(OrderViews::accessLog)
            .toList();
    }

    /** Exactly what the client page shows; not written to the access log. */
    @Transactional(readOnly = true)
    public ClientOrderView clientPreview(Actor actor, UUID orderId) {
        return clientViews.build(lookup.forRead(actor, orderId));
    }

    // ── helpers ──

    private void checkLotIsNew(ImportOrder order) {
        if (order.getLotNumber() == null) return;
        boolean taken = order.getId() == null
            ? orders.existsByDealerIdAndSourceAndLotNumber(order.getDealerId(), order.getSource(), order.getLotNumber())
            : orders.existsByDealerIdAndSourceAndLotNumberAndIdNot(order.getDealerId(), order.getSource(),
                order.getLotNumber(), order.getId());
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have an order for this lot");
        }
    }

    private String newUniqueCode() {
        String code;
        do {
            code = credentials.newPublicCode();
        } while (orders.existsByPublicCode(code));
        return code;
    }

    private String hash(String password) {
        return passwordEncoder.encode(ClientCredentialsGenerator.normalize(password));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
