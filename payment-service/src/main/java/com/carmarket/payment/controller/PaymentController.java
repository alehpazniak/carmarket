package com.carmarket.payment.controller;

import com.carmarket.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.carmarket.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.carmarket.payment.dto.PaymentDtos.PaymentResponse;
import com.carmarket.payment.dto.PaymentDtos.ProductResponse;
import com.carmarket.payment.dto.PaymentDtos.RefundRequest;
import com.carmarket.payment.dto.PaymentDtos.TestAccessResponse;
import com.carmarket.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Reached through the gateway as /api/payments/**. */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return paymentService.listProducts();
    }

    /** Starts a payment; the client must redirect the browser to redirectUrl. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse create(@AuthenticationPrincipal String userId,
                                        @RequestHeader(value = "X-User-Email", required = false) String email,
                                        @Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.create(UUID.fromString(userId), email, request);
    }

    @GetMapping("/my")
    public List<PaymentResponse> myPayments(@AuthenticationPrincipal String userId) {
        return paymentService.listForUser(UUID.fromString(userId));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return paymentService.get(UUID.fromString(userId), id);
    }

    /** Return page asks P24 for the current state in case the notification is late. */
    @PostMapping("/{id}/sync")
    public PaymentResponse sync(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return paymentService.sync(UUID.fromString(userId), id);
    }

    // ─── Admin ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id, @Valid @RequestBody(required = false) RefundRequest request) {
        return paymentService.refund(id, request != null ? request.description() : null);
    }

    /** Checks the configured P24 credentials against /api/v1/testAccess. */
    @GetMapping("/admin/p24/test-access")
    public TestAccessResponse testAccess() {
        return paymentService.testAccess();
    }
}
