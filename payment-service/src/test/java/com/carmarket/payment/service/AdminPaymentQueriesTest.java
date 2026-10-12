package com.carmarket.payment.service;

import com.carmarket.payment.audit.AdminAuditPublisher;
import com.carmarket.payment.entity.Payment;
import com.carmarket.payment.entity.PaymentStatus;
import com.carmarket.payment.repository.PaymentRepository;
import com.carmarket.payment.service.AdminPaymentService.PaymentFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/** Filters, stats and CSV against a real Postgres (Flyway migrations, ddl validate as in the service). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminPaymentService.class)
@Testcontainers
class AdminPaymentQueriesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private AdminAuditPublisher audit;
    @Autowired
    private AdminPaymentService service;
    @Autowired
    private PaymentRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private Payment save(UUID user, String product, int amount, PaymentStatus status, String email) {
        UUID id = UUID.randomUUID();
        Payment p = repository.saveAndFlush(Payment.builder().id(id).sessionId(id.toString()).userId(user).productCode(product)
            .amount(amount).currency("PLN").description("Promowanie").email(email).status(status)
            .paidAt(status == PaymentStatus.PAID || status == PaymentStatus.REFUNDED ? Instant.now() : null)
            .refundedAt(status == PaymentStatus.REFUNDED ? Instant.now() : null).build());
        return p;
    }

    @BeforeEach
    void setUp() {
        save(alice, "LISTING_PROMOTION", 1999, PaymentStatus.PAID, "alice@example.com");
        save(alice, "LISTING_HIGHLIGHT", 999, PaymentStatus.REFUNDED, "=cmd|' /C calc'!A0");
        save(bob, "LISTING_PROMOTION", 1999, PaymentStatus.PENDING, "bob;semi@example.com");
        Payment old = save(bob, "LISTING_PROMOTION", 1999, PaymentStatus.PAID, "bob@example.com");
        jdbc.update("UPDATE payments SET created_at = ?, paid_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)), Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)), old.getId());
    }

    @Test
    void filtersCombine() {
        assertThat(service.search(new PaymentFilter(null, null, alice, null, null), PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
        assertThat(service.search(new PaymentFilter(PaymentStatus.PAID, "LISTING_PROMOTION", null, null, null), PageRequest.of(0, 20))
            .getTotalElements()).isEqualTo(2);
        assertThat(service.search(new PaymentFilter(null, null, bob, LocalDate.now().minusDays(7), null), PageRequest.of(0, 20))
            .getContent()).singleElement().extracting(v -> v.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void statsCountOnlyPaidRevenueInTheRange() {
        var stats = service.stats(null, null);
        assertThat(stats.revenue()).isEqualTo(1999);
        assertThat(stats.refundedAmount()).isEqualTo(999);
        assertThat(stats.refundedCount()).isEqualTo(1);
        assertThat(stats.countByStatus().get("PENDING")).isEqualTo(1);
        assertThat(stats.daily()).hasSize(30);
        assertThat(stats.daily().get(29).amount()).isEqualTo(1999);
    }

    @Test
    void csvHasBomHeaderSemicolonsAndNeutralisedFormulas() throws Exception {
        StringWriter out = new StringWriter();
        boolean truncated = service.writeCsv(new PaymentFilter(null, null, null, null, null), out,
            new AdminAuditPublisher.Actor("admin-1", "boss@example.com"));

        String csv = out.toString();
        assertThat(truncated).isFalse();
        assertThat(csv).startsWith("\uFEFFid;utworzono;status;produkt;kwota;waluta;uzytkownik;email;referencja;zaplacono;zwrocono;p24_order_id\r\n");
        assertThat(csv.split("\r\n")).hasSize(5);
        assertThat(csv).contains(";19,99;PLN;").contains("\"bob;semi@example.com\"").contains("'=cmd|' /C calc'!A0");
        verify(audit).publish(any(), eq("PAYMENTS_EXPORTED"), eq("PAYMENT"), isNull(), isNull(), any());
    }
}
