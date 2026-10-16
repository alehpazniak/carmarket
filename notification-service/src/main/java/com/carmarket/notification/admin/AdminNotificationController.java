package com.carmarket.notification.admin;

import com.carmarket.notification.audit.AdminAuditPublisher;
import com.carmarket.notification.entity.NotificationStatus;
import com.carmarket.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Gateway: /api/notifications/admin/** (the only notification-service route) — ADMIN only. */
@RestController
@RequestMapping("/notifications/admin")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService service;

    @GetMapping("/notifications")
    public Page<AdminNotificationService.NotificationView> list(@RequestParam(required = false) NotificationStatus status,
                                                                @RequestParam(required = false) NotificationType type,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return service.list(status, type, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @PostMapping("/notifications/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable UUID id, @AuthenticationPrincipal String adminId,
                      @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        service.retry(id, new AdminAuditPublisher.Actor(adminId, adminEmail));
    }

    @GetMapping("/stats")
    public AdminNotificationService.NotificationStats stats() {
        return service.stats();
    }
}
