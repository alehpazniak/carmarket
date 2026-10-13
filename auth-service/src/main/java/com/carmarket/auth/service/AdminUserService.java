package com.carmarket.auth.service;

import com.carmarket.auth.audit.AdminAuditPublisher;
import com.carmarket.auth.audit.AdminAuditPublisher.Actor;
import com.carmarket.auth.dto.AdminUserDtos.AdminUserView;
import com.carmarket.auth.dto.AdminUserDtos.DailyCount;
import com.carmarket.auth.dto.AdminUserDtos.DealerRequestView;
import com.carmarket.auth.dto.AdminUserDtos.UserStats;
import com.carmarket.auth.entity.DealerUpgradeRequest;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.repository.DealerUpgradeRequestRepository;
import com.carmarket.auth.repository.UserRepository;
import com.carmarket.auth.support.AfterCommit;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin actions on accounts. auth-service is the source of truth for role, active flag and email;
 * user-service, car-service and chat-service follow through user.role-changed / user.blocked /
 * user.unblocked. Every write is audited, and every event and Redis key is written after commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    static final String TOPIC_ROLE_CHANGED = "user.role-changed";
    static final String TOPIC_BLOCKED = "user.blocked";
    static final String TOPIC_UNBLOCKED = "user.unblocked";

    private final UserRepository userRepository;
    private final DealerUpgradeRequestRepository dealerRequestRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccessRevocationService revocation;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AdminAuditPublisher auditPublisher;

    // ─── READ ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminUserView> search(String q, User.UserRole role, Boolean active, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("email")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("displayName"), "")), like, '\\')));
            }
            if (role != null) p.add(cb.equal(root.get("role"), role));
            if (active != null) p.add(cb.equal(root.get("active"), active));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return userRepository.findAll(spec, pageable).map(AdminUserView::from);
    }

    @Transactional(readOnly = true)
    public AdminUserView get(UUID id) {
        return AdminUserView.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<DealerRequestView> dealerRequests(String state, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<DealerUpgradeRequest> page = switch (state == null ? "pending" : state) {
            case "pending" -> dealerRequestRepository.findByConfirmedAtIsNullAndExpiresAtAfter(now, pageable);
            case "expired" -> dealerRequestRepository.findByConfirmedAtIsNullAndExpiresAtLessThanEqual(now, pageable);
            case "confirmed" -> dealerRequestRepository.findByConfirmedAtIsNotNull(pageable);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state must be pending, confirmed or expired");
        };
        Set<UUID> userIds = page.getContent().stream().map(DealerUpgradeRequest::getUserId).collect(Collectors.toSet());
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        return page.map(r -> DealerRequestView.from(r, users.get(r.getUserId()), now));
    }

    @Transactional(readOnly = true)
    public UserStats stats() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (User.UserRole role : User.UserRole.values()) byRole.put(role.name(), 0L);
        long total = 0;
        for (Object[] row : userRepository.countByRole()) {
            long count = ((Number) row[1]).longValue();
            byRole.put(((User.UserRole) row[0]).name(), count);
            total += count;
        }

        LocalDate firstDay = now.toLocalDate().minusDays(29);
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (Object[] row : userRepository.dailyRegistrations(firstDay.atStartOfDay())) {
            perDay.put(((Date) row[0]).toLocalDate(), ((Number) row[1]).longValue());
        }
        List<DailyCount> daily = new ArrayList<>();
        for (LocalDate d = firstDay; !d.isAfter(now.toLocalDate()); d = d.plusDays(1)) {
            daily.add(new DailyCount(d, perDay.getOrDefault(d, 0L)));
        }

        return new UserStats(total, byRole, userRepository.countByActiveFalse(),
            userRepository.countByCreatedAtAfter(now.minusDays(7)),
            userRepository.countByCreatedAtAfter(now.minusDays(30)),
            daily, dealerRequestRepository.countByConfirmedAtIsNullAndExpiresAtAfter(now));
    }

    // ─── WRITE ────────────────────────────────────────────────────────────

    @Transactional
    public AdminUserView changeRole(UUID id, User.UserRole role, String reason, Actor admin) {
        requireNotSelf(id, admin, "You can't change your own role");
        User user = find(id);
        User.UserRole previous = user.getRole();
        if (previous == role) {
            return AdminUserView.from(user);
        }
        if (previous == User.UserRole.ADMIN && user.isActive()) {
            requireAnotherActiveAdmin(user);
        }
        user.setRole(role);
        userRepository.save(user);

        String userId = id.toString();
        AfterCommit.run(() -> {
            kafkaTemplate.send(TOPIC_ROLE_CHANGED, userId, Map.of("userId", userId, "role", role.name()));
            safely(() -> revocation.invalidateIssuedTokens(userId), "min-iat", userId);
        });
        auditPublisher.publish(admin, "USER_ROLE_CHANGED", "USER", userId, reason,
            Map.of("from", previous.name(), "to", role.name()));
        log.info("Admin {} changed role of {} from {} to {}", admin.id(), userId, previous, role);
        return AdminUserView.from(user);
    }

    @Transactional
    public AdminUserView block(UUID id, String reason, Actor admin) {
        requireNotSelf(id, admin, "You can't block yourself");
        User user = find(id);
        if (!user.isActive()) {
            return AdminUserView.from(user);
        }
        if (user.getRole() == User.UserRole.ADMIN) {
            requireAnotherActiveAdmin(user);
        }
        user.setActive(false);
        userRepository.save(user);

        String userId = id.toString();
        Map<String, Object> event = statusEvent(userId, reason);
        AfterCommit.run(() -> {
            safely(() -> revocation.markBlocked(userId), "blocked flag", userId);
            safely(() -> refreshTokenService.revokeAll(userId), "refresh tokens", userId);
            kafkaTemplate.send(TOPIC_BLOCKED, userId, event);
        });
        auditPublisher.publish(admin, "USER_BLOCKED", "USER", userId, reason, Map.of("email", user.getEmail()));
        log.info("Admin {} blocked user {}", admin.id(), userId);
        return AdminUserView.from(user);
    }

    @Transactional
    public AdminUserView unblock(UUID id, String reason, Actor admin) {
        User user = find(id);
        if (user.isActive()) {
            return AdminUserView.from(user);
        }
        user.setActive(true);
        userRepository.save(user);

        String userId = id.toString();
        Map<String, Object> event = statusEvent(userId, reason);
        AfterCommit.run(() -> {
            safely(() -> revocation.clearBlocked(userId), "blocked flag", userId);
            kafkaTemplate.send(TOPIC_UNBLOCKED, userId, event);
        });
        auditPublisher.publish(admin, "USER_UNBLOCKED", "USER", userId, reason, Map.of("email", user.getEmail()));
        log.info("Admin {} unblocked user {}", admin.id(), userId);
        return AdminUserView.from(user);
    }

    /** Ends every session of the user: refresh tokens are revoked and current access tokens rejected. */
    @Transactional(readOnly = true)
    public void logoutEverywhere(UUID id, String reason, Actor admin) {
        User user = find(id);
        String userId = user.getId().toString();
        refreshTokenService.revokeAll(userId);
        safely(() -> revocation.invalidateIssuedTokens(userId), "min-iat", userId);
        auditPublisher.publish(admin, "USER_LOGGED_OUT", "USER", userId, reason, Map.of());
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private User find(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static void requireNotSelf(UUID target, Actor admin, String message) {
        if (target.toString().equals(admin.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    /** 409 unless another active admin remains; runs under a row lock on the active admins. */
    private void requireAnotherActiveAdmin(User user) {
        boolean another = userRepository.lockActiveAdmins().stream().anyMatch(a -> !a.getId().equals(user.getId()));
        if (!another) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active admin can't be demoted or blocked");
        }
    }

    private static Map<String, Object> statusEvent(String userId, String reason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("userId", userId);
        event.put("reason", reason);
        event.put("occurredAt", Instant.now().toString());
        return event;
    }

    /**
     * Redis is best effort: the database change is committed and refresh is checked against it, so a
     * Redis failure only delays the effect until the current access token expires.
     */
    private static void safely(Runnable action, String what, String userId) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Could not update {} in Redis for user {}: {}", what, userId, e.getMessage());
        }
    }
}
