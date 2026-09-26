package com.carmarket.payment.service;

import com.carmarket.payment.config.PaymentProductProperties;
import com.carmarket.payment.config.PaymentProductProperties.Product;
import com.carmarket.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.carmarket.payment.dto.PaymentDtos.CreatePaymentResponse;
import com.carmarket.payment.dto.PaymentDtos.PaymentResponse;
import com.carmarket.payment.dto.PaymentDtos.ProductResponse;
import com.carmarket.payment.dto.PaymentDtos.TestAccessResponse;
import com.carmarket.payment.entity.Payment;
import com.carmarket.payment.entity.PaymentStatus;
import com.carmarket.payment.exception.PaymentException;
import com.carmarket.payment.kafka.PaymentEventProducer;
import com.carmarket.payment.przelewy24.P24Notification;
import com.carmarket.payment.przelewy24.P24Requests.TransactionDetails;
import com.carmarket.payment.przelewy24.Przelewy24Client;
import com.carmarket.payment.przelewy24.Przelewy24Exception;
import com.carmarket.payment.przelewy24.Przelewy24Properties;
import com.carmarket.payment.przelewy24.Przelewy24SignCalculator;
import com.carmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Przelewy24 payment flow:
 * <ol>
 *   <li>{@link #create} saves a PENDING payment, registers it at P24 and returns the payment-page URL.</li>
 *   <li>After paying, P24 POSTs to urlStatus → {@link #handleNotification} checks the sign,
 *       calls transaction/verify and marks the payment PAID.</li>
 *   <li>{@link #sync} lets the return page confirm the payment when the notification hasn't
 *       arrived yet (or can't, e.g. on localhost).</li>
 * </ol>
 * A payment is only PAID after a successful transaction/verify — before that P24 does not settle the money.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int MAX_EMAIL_LENGTH = 50;

    private final PaymentRepository paymentRepository;
    private final PaymentProductProperties productProperties;
    private final Przelewy24Properties p24Properties;
    private final Przelewy24Client p24Client;
    private final Przelewy24SignCalculator signCalculator;
    private final PaymentEventProducer eventProducer;

    public List<ProductResponse> listProducts() {
        return productProperties.getProducts().entrySet().stream()
            .map(e -> new ProductResponse(e.getKey(), e.getValue().getAmount(),
                e.getValue().getCurrency(), e.getValue().getDescription()))
            .toList();
    }

    /** Not @Transactional on purpose: the PENDING row must be committed before P24 is called. */
    public CreatePaymentResponse create(UUID userId, String tokenEmail, CreatePaymentRequest request) {
        requireConfigured();
        Product product = productProperties.find(request.productCode())
            .orElseThrow(() -> new PaymentException(HttpStatus.BAD_REQUEST,
                "Unknown product: " + request.productCode()));
        String email = resolveEmail(tokenEmail, request.email());

        UUID id = UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.builder()
            .id(id)
            .sessionId(id.toString())
            .userId(userId)
            .productCode(request.productCode())
            .referenceId(request.referenceId())
            .amount(product.getAmount())
            .currency(product.getCurrency())
            .description(product.getDescription())
            .email(email)
            .status(PaymentStatus.PENDING)
            .build());

        String token;
        try {
            token = p24Client.registerTransaction(payment.getSessionId(), payment.getAmount(),
                payment.getCurrency(), payment.getDescription(), email, returnUrl(id));
        } catch (Przelewy24Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(truncate(e.getMessage(), 255));
            paymentRepository.save(payment);
            throw e;
        }

        payment.setP24Token(token);
        paymentRepository.save(payment);
        log.info("Registered P24 transaction {} for user {} ({} {} {})",
            payment.getSessionId(), userId, payment.getProductCode(), payment.getAmount(), payment.getCurrency());
        return new CreatePaymentResponse(id, payment.getStatus(), p24Properties.paymentPageUrl(token));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID userId, UUID paymentId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
            .map(PaymentResponse::from)
            .orElseThrow(PaymentException::notFound);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForUser(UUID userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(PaymentResponse::from)
            .toList();
    }

    /**
     * Handles the urlStatus webhook. Throws on anything suspicious so P24 gets a non-2xx
     * answer and retries (after 3, 5, 15, 30, 60, 150, 450 min).
     */
    @Transactional
    public void handleNotification(P24Notification n) {
        requireConfigured();
        if (n.sessionId() == null || n.orderId() == null || n.amount() == null
            || n.currency() == null || n.merchantId() == null || n.posId() == null) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Incomplete notification");
        }
        String expectedSign = signCalculator.notificationSign(n, p24Properties.getCrc());
        if (!signCalculator.matches(expectedSign, n.sign())) {
            log.warn("Rejected P24 notification with invalid sign for session {}", n.sessionId());
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Invalid sign");
        }
        if (!n.merchantId().equals(p24Properties.getMerchantId())
            || !n.posId().equals(p24Properties.effectivePosId())) {
            log.warn("Rejected P24 notification for foreign merchant {} / pos {}", n.merchantId(), n.posId());
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Unknown merchant");
        }

        Payment payment = paymentRepository.findBySessionIdForUpdate(n.sessionId())
            .orElseThrow(PaymentException::notFound);

        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Duplicate P24 notification for already settled payment {}", payment.getId());
            return;
        }
        if (!n.amount().equals(payment.getAmount()) || !n.currency().equals(payment.getCurrency())) {
            log.error("P24 notification amount mismatch for payment {}: got {} {}, expected {} {}",
                payment.getId(), n.amount(), n.currency(), payment.getAmount(), payment.getCurrency());
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Amount mismatch");
        }

        p24Client.verifyTransaction(payment.getSessionId(), n.orderId(), payment.getAmount(), payment.getCurrency());
        markPaid(payment, n.orderId(), n.methodId(), n.statement());
    }

    /**
     * Called by the return page: asks P24 for the transaction state and completes the payment
     * if the customer paid but the notification hasn't been processed yet.
     */
    @Transactional
    public PaymentResponse sync(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
            .filter(p -> p.getUserId().equals(userId))
            .orElseThrow(PaymentException::notFound);
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getP24Token() == null
            || !p24Properties.isConfigured()) {
            return PaymentResponse.from(payment);
        }

        Optional<TransactionDetails> details = p24Client.getTransaction(payment.getSessionId());
        if (details.isEmpty() || details.get().orderId() == null || details.get().status() == null) {
            return PaymentResponse.from(payment);
        }
        TransactionDetails t = details.get();
        if (!payment.getAmount().equals(t.amount())) {
            log.error("P24 amount mismatch on sync for payment {}: got {}, expected {}",
                payment.getId(), t.amount(), payment.getAmount());
            return PaymentResponse.from(payment);
        }

        if (t.status() == TransactionDetails.STATUS_ADVANCE_PAYMENT) {
            p24Client.verifyTransaction(payment.getSessionId(), t.orderId(), payment.getAmount(), payment.getCurrency());
            markPaid(payment, t.orderId(), t.paymentMethod(), t.statement());
        } else if (t.status() == TransactionDetails.STATUS_PAYMENT_MADE) {
            // Already verified on the P24 side (e.g. notification verified but our commit failed)
            markPaid(payment, t.orderId(), t.paymentMethod(), t.statement());
        }
        return PaymentResponse.from(payment);
    }

    /** Full refund of a PAID payment (admin only). P24 executes it asynchronously. */
    @Transactional
    public PaymentResponse refund(UUID paymentId, String description) {
        requireConfigured();
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(PaymentException::notFound);
        if (payment.getStatus() != PaymentStatus.PAID || payment.getP24OrderId() == null) {
            throw new PaymentException(HttpStatus.CONFLICT, "Only paid payments can be refunded");
        }

        String refundDescription = description != null && !description.isBlank()
            ? description : "Zwrot " + payment.getProductCode();
        p24Client.refund(UUID.randomUUID().toString(), payment.getId().toString().replace("-", ""),
            payment.getP24OrderId(), payment.getSessionId(), payment.getAmount(), truncate(refundDescription, 35));

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());
        eventProducer.publishRefunded(payment);
        log.info("Refund requested for payment {}", payment.getId());
        return PaymentResponse.from(payment);
    }

    public TestAccessResponse testAccess() {
        if (!p24Properties.isConfigured()) {
            return new TestAccessResponse(false, p24Properties.isSandbox(), false);
        }
        return new TestAccessResponse(true, p24Properties.isSandbox(), p24Client.testAccess());
    }

    private void markPaid(Payment payment, Long orderId, Integer methodId, String statement) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setP24OrderId(orderId);
        payment.setP24MethodId(methodId);
        payment.setP24Statement(truncate(statement, 255));
        payment.setPaidAt(Instant.now());
        eventProducer.publishCompleted(payment);
        log.info("Payment {} PAID (P24 order {})", payment.getId(), orderId);
    }

    private void requireConfigured() {
        if (!p24Properties.isConfigured()) {
            throw PaymentException.notConfigured();
        }
    }

    private String resolveEmail(String tokenEmail, String requestEmail) {
        String email = tokenEmail != null && !tokenEmail.isBlank() ? tokenEmail : requestEmail;
        if (email == null || email.isBlank()) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Email is required for payment");
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Email is too long for Przelewy24 (max 50)");
        }
        return email;
    }

    private String returnUrl(UUID paymentId) {
        String base = p24Properties.getUrlReturn();
        return (base.endsWith("/") ? base : base + "/") + paymentId;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
