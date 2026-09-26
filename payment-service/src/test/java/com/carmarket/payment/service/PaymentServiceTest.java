package com.carmarket.payment.service;

import com.carmarket.payment.config.PaymentProductProperties;
import com.carmarket.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.carmarket.payment.dto.PaymentDtos.CreatePaymentResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final String CRC = "crc-key";
    private static final UUID USER_ID = UUID.randomUUID();

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final Przelewy24Client client = mock(Przelewy24Client.class);
    private final PaymentEventProducer producer = mock(PaymentEventProducer.class);
    private final Przelewy24SignCalculator signCalculator = new Przelewy24SignCalculator();
    private final Przelewy24Properties p24 = new Przelewy24Properties();
    private PaymentService service;

    @BeforeEach
    void setUp() {
        p24.setMerchantId(100);
        p24.setCrc(CRC);
        p24.setApiKey("api-key");
        p24.setUrlReturn("http://localhost:5173/platnosc");
        p24.setUrlStatus("https://example.com/api/payments/p24/notify");

        PaymentProductProperties.Product product = new PaymentProductProperties.Product();
        product.setAmount(1999);
        product.setDescription("Promowanie ogłoszenia");
        PaymentProductProperties products = new PaymentProductProperties();
        products.setProducts(Map.of("LISTING_PROMOTION", product));

        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PaymentService(repository, products, p24, client, signCalculator, producer);
    }

    @Test
    void createRegistersTransactionWithServerSidePrice() {
        when(client.registerTransaction(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("TOKEN123");

        CreatePaymentResponse response = service.create(USER_ID, "buyer@example.com",
            new CreatePaymentRequest("LISTING_PROMOTION", "car-1", null));

        assertThat(response.redirectUrl()).isEqualTo("https://sandbox.przelewy24.pl/trnRequest/TOKEN123");
        verify(client).registerTransaction(eq(response.paymentId().toString()), eq(1999), eq("PLN"),
            eq("Promowanie ogłoszenia"), eq("buyer@example.com"),
            eq("http://localhost:5173/platnosc/" + response.paymentId()));
    }

    @Test
    void createFailsWith503WhenCredentialsMissing() {
        p24.setCrc(null);
        assertThatThrownBy(() -> service.create(USER_ID, "a@b.pl", new CreatePaymentRequest("LISTING_PROMOTION", null, null)))
            .isInstanceOf(PaymentException.class)
            .extracting(e -> ((PaymentException) e).getStatus())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void createRejectsUnknownProduct() {
        assertThatThrownBy(() -> service.create(USER_ID, "a@b.pl", new CreatePaymentRequest("FREE_CAR", null, null)))
            .isInstanceOf(PaymentException.class)
            .hasMessageContaining("Unknown product");
    }

    @Test
    void createMarksPaymentFailedWhenRegistrationFails() {
        when(client.registerTransaction(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new Przelewy24Exception("boom"));

        assertThatThrownBy(() -> service.create(USER_ID, "a@b.pl", new CreatePaymentRequest("LISTING_PROMOTION", null, null)))
            .isInstanceOf(Przelewy24Exception.class);
        verify(repository, org.mockito.Mockito.atLeastOnce())
            .save(org.mockito.ArgumentMatchers.argThat(p -> p.getStatus() == PaymentStatus.FAILED));
    }

    @Test
    void validNotificationVerifiesAndMarksPaid() {
        Payment payment = pendingPayment();
        when(repository.findBySessionIdForUpdate(payment.getSessionId())).thenReturn(Optional.of(payment));

        service.handleNotification(signed(payment.getSessionId(), 1999, "PLN"));

        verify(client).verifyTransaction(payment.getSessionId(), 555L, 1999, "PLN");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getP24OrderId()).isEqualTo(555L);
        verify(producer).publishCompleted(payment);
    }

    @Test
    void notificationWithBadSignIsRejected() {
        Payment payment = pendingPayment();
        P24Notification good = signed(payment.getSessionId(), 1999, "PLN");
        P24Notification forged = new P24Notification(good.merchantId(), good.posId(), good.sessionId(), good.amount(),
            good.originAmount(), good.currency(), good.orderId(), good.methodId(), good.statement(), "00ff");

        assertThatThrownBy(() -> service.handleNotification(forged)).hasMessage("Invalid sign");
        verify(client, never()).verifyTransaction(anyString(), anyLong(), anyInt(), anyString());
    }

    @Test
    void notificationWithDifferentAmountIsRejected() {
        Payment payment = pendingPayment();
        when(repository.findBySessionIdForUpdate(payment.getSessionId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.handleNotification(signed(payment.getSessionId(), 1, "PLN")))
            .hasMessage("Amount mismatch");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void duplicateNotificationIsIgnored() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PAID);
        when(repository.findBySessionIdForUpdate(payment.getSessionId())).thenReturn(Optional.of(payment));

        service.handleNotification(signed(payment.getSessionId(), 1999, "PLN"));

        verify(client, never()).verifyTransaction(anyString(), anyLong(), anyInt(), anyString());
        verify(producer, never()).publishCompleted(any());
    }

    @Test
    void syncVerifiesAdvancePayment() {
        Payment payment = pendingPayment();
        when(repository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(client.getTransaction(payment.getSessionId())).thenReturn(Optional.of(
            new TransactionDetails(777L, payment.getSessionId(), TransactionDetails.STATUS_ADVANCE_PAYMENT,
                1999, "PLN", 25, "stmt")));

        service.sync(USER_ID, payment.getId());

        verify(client).verifyTransaction(payment.getSessionId(), 777L, 1999, "PLN");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void syncOfAnotherUsersPaymentIsNotFound() {
        Payment payment = pendingPayment();
        when(repository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.sync(UUID.randomUUID(), payment.getId()))
            .hasMessage("Payment not found");
    }

    private Payment pendingPayment() {
        UUID id = UUID.randomUUID();
        return Payment.builder()
            .id(id)
            .sessionId(id.toString())
            .userId(USER_ID)
            .productCode("LISTING_PROMOTION")
            .amount(1999)
            .currency("PLN")
            .description("Promowanie ogłoszenia")
            .email("buyer@example.com")
            .status(PaymentStatus.PENDING)
            .p24Token("TOKEN")
            .build();
    }

    private P24Notification signed(String sessionId, int amount, String currency) {
        P24Notification unsigned = new P24Notification(100, 100, sessionId, amount, amount, currency, 555L, 25, "stmt", null);
        String sign = signCalculator.notificationSign(unsigned, CRC);
        return new P24Notification(100, 100, sessionId, amount, amount, currency, 555L, 25, "stmt", sign);
    }
}
