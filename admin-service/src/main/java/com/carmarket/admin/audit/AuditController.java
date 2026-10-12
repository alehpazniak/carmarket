package com.carmarket.admin.audit;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GET /admin/audit — newest first; every filter is optional, from/to are ISO instants. */
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repository;

    public record AuditEntryView(UUID id, Instant occurredAt, String actorId, String actorEmail, String service,
                                 String action, String targetType, String targetId, String reason,
                                 Map<String, Object> details) {
        static AuditEntryView from(AuditLogEntry e) {
            return new AuditEntryView(e.getId(), e.getOccurredAt(), e.getActorId(), e.getActorEmail(), e.getService(),
                e.getAction(), e.getTargetType(), e.getTargetId(), e.getReason(), e.getDetails());
        }
    }

    @GetMapping
    public Page<AuditEntryView> search(@RequestParam(required = false) String actorId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) String targetType,
                                       @RequestParam(required = false) String targetId,
                                       @RequestParam(required = false) String service,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (notBlank(actorId)) p.add(cb.equal(root.get("actorId"), actorId.trim()));
            if (notBlank(action)) p.add(cb.equal(root.get("action"), action.trim()));
            if (notBlank(targetType)) p.add(cb.equal(root.get("targetType"), targetType.trim()));
            if (notBlank(targetId)) p.add(cb.equal(root.get("targetId"), targetId.trim()));
            if (notBlank(service)) p.add(cb.equal(root.get("service"), service.trim()));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("occurredAt"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "receivedAt")));
        return repository.findAll(spec, pageable).map(AuditEntryView::from);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
