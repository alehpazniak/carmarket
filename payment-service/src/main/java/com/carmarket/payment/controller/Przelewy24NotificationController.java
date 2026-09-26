package com.carmarket.payment.controller;

import com.carmarket.payment.przelewy24.P24Notification;
import com.carmarket.payment.przelewy24.Przelewy24Properties;
import com.carmarket.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Przelewy24 urlStatus webhook. Public (no JWT) — authenticity comes from the sign checksum
 * plus the transaction/verify call, and optionally from the P24 source-IP allow-list.
 */
@Slf4j
@RestController
@RequestMapping("/payments/p24")
public class Przelewy24NotificationController {

    private final PaymentService paymentService;
    private final Przelewy24Properties properties;
    private final List<IpAddressMatcher> allowedIps;

    public Przelewy24NotificationController(PaymentService paymentService, Przelewy24Properties properties) {
        this.paymentService = paymentService;
        this.properties = properties;
        this.allowedIps = properties.getNotificationIps().stream().map(IpAddressMatcher::new).toList();
    }

    @PostMapping("/notify")
    public ResponseEntity<Void> notify(@RequestBody P24Notification notification, HttpServletRequest request) {
        String sourceIp = clientIp(request);
        if (properties.isVerifyNotificationIp() && allowedIps.stream().noneMatch(m -> m.matches(sourceIp))) {
            log.warn("Rejected P24 notification from non-P24 address {}", sourceIp);
            return ResponseEntity.status(403).build();
        }
        log.info("P24 notification for session {} (order {}) from {}",
            notification.sessionId(), notification.orderId(), sourceIp);
        paymentService.handleNotification(notification);
        return ResponseEntity.ok().build();
    }

    /**
     * The gateway appends the caller address to X-Forwarded-For, so the last entry is the one it saw;
     * earlier entries are client-supplied and can't be trusted.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
