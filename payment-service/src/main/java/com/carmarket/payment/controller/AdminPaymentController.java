package com.carmarket.payment.controller;

import com.carmarket.payment.audit.AdminAuditPublisher;
import com.carmarket.payment.dto.AdminPaymentDtos.AdminPaymentView;
import com.carmarket.payment.dto.AdminPaymentDtos.PaymentStats;
import com.carmarket.payment.entity.PaymentStatus;
import com.carmarket.payment.service.AdminPaymentService;
import com.carmarket.payment.service.AdminPaymentService.PaymentFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** Gateway: /api/payments/admin/** — ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/payments/admin")
@RequiredArgsConstructor
public class AdminPaymentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminPaymentService service;

    @GetMapping("/payments")
    public Page<AdminPaymentView> payments(@RequestParam(required = false) PaymentStatus status,
                                           @RequestParam(required = false) String productCode,
                                           @RequestParam(required = false) UUID userId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.search(new PaymentFilter(status, productCode, userId, from, to),
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/payments/{id}")
    public AdminPaymentView payment(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Same filters as the list; X-Export-Truncated: true when more than 10,000 rows matched. */
    @GetMapping("/payments.csv")
    public void csv(@RequestParam(required = false) PaymentStatus status,
                    @RequestParam(required = false) String productCode,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @AuthenticationPrincipal String adminId,
                    @RequestHeader(value = "X-User-Email", required = false) String adminEmail,
                    HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"platnosci-" + LocalDate.now(AdminPaymentService.ZONE) + ".csv\"");
        // Headers must be set before the body: build the CSV in memory (≤ 10,000 short rows)
        java.io.StringWriter buffer = new java.io.StringWriter();
        boolean truncated = service.writeCsv(new PaymentFilter(status, productCode, userId, from, to), buffer,
            new AdminAuditPublisher.Actor(adminId, adminEmail));
        response.setHeader("X-Export-Truncated", String.valueOf(truncated));
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition, X-Export-Truncated");
        try (Writer out = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            out.write(buffer.toString());
        }
    }

    @GetMapping("/stats")
    public PaymentStats stats(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.stats(from, to);
    }
}
