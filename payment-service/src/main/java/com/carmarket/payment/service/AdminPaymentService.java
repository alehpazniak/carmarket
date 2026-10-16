package com.carmarket.payment.service;

import com.carmarket.payment.audit.AdminAuditPublisher;
import com.carmarket.payment.dto.AdminPaymentDtos.AdminPaymentView;
import com.carmarket.payment.dto.AdminPaymentDtos.DailyRevenue;
import com.carmarket.payment.dto.AdminPaymentDtos.PaymentStats;
import com.carmarket.payment.dto.AdminPaymentDtos.ProductRevenue;
import com.carmarket.payment.entity.Payment;
import com.carmarket.payment.entity.PaymentStatus;
import com.carmarket.payment.exception.PaymentException;
import com.carmarket.payment.repository.PaymentRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin reads over all payments; days and the CSV use Europe/Warsaw, like the rest of the site. */
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    public static final int CSV_MAX_ROWS = 10_000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE);
    private static final String[] CSV_HEADER = {"id", "utworzono", "status", "produkt", "kwota", "waluta", "uzytkownik",
        "email", "referencja", "zaplacono", "zwrocono", "p24_order_id"};

    private final PaymentRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditPublisher auditPublisher;

    /** Optional filters; from/to are local days (inclusive) on createdAt. */
    public record PaymentFilter(PaymentStatus status, String productCode, UUID userId, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public Page<AdminPaymentView> search(PaymentFilter filter, Pageable pageable) {
        return repository.findAll(spec(filter), pageable).map(AdminPaymentView::from);
    }

    @Transactional(readOnly = true)
    public AdminPaymentView get(UUID id) {
        return repository.findById(id).map(AdminPaymentView::from).orElseThrow(PaymentException::notFound);
    }

    /**
     * Semicolon-separated, UTF-8 with BOM (so Polish Excel picks the encoding), amounts with a decimal
     * comma, at most {@value #CSV_MAX_ROWS} newest rows. The export contains emails, so it is audited.
     *
     * @return whether more rows matched than were written
     */
    @Transactional(readOnly = true)
    public boolean writeCsv(PaymentFilter filter, Writer out, AdminAuditPublisher.Actor admin) throws IOException {
        Page<Payment> page = repository.findAll(spec(filter),
            PageRequest.of(0, CSV_MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt")));
        out.write('\uFEFF');
        out.write(String.join(";", CSV_HEADER));
        out.write("\r\n");
        for (Payment p : page.getContent()) {
            out.write(String.join(";",
                csv(p.getId().toString()), csv(time(p.getCreatedAt())), csv(p.getStatus().name()), csv(p.getProductCode()),
                csv(new BigDecimal(p.getAmount()).movePointLeft(2).toPlainString().replace('.', ',')), csv(p.getCurrency()),
                csv(p.getUserId().toString()), csv(p.getEmail()), csv(p.getReferenceId()), csv(time(p.getPaidAt())),
                csv(time(p.getRefundedAt())), csv(p.getP24OrderId() != null ? p.getP24OrderId().toString() : null)));
            out.write("\r\n");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rows", page.getNumberOfElements());
        if (filter.status() != null) details.put("status", filter.status().name());
        if (filter.productCode() != null) details.put("productCode", filter.productCode());
        if (filter.userId() != null) details.put("userId", filter.userId().toString());
        if (filter.from() != null) details.put("from", filter.from().toString());
        if (filter.to() != null) details.put("to", filter.to().toString());
        auditPublisher.publish(admin, "PAYMENTS_EXPORTED", "PAYMENT", null, null, details);
        return page.getTotalElements() > page.getNumberOfElements();
    }

    /** Revenue = PAID payments by paid_at day; refunds are reported separately (by refunded_at). */
    @Transactional(readOnly = true)
    public PaymentStats stats(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZONE);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) throw new IllegalArgumentException("from must not be after to");
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", Timestamp.from(start.atStartOfDay(ZONE).toInstant()))
            .addValue("to", Timestamp.from(end.plusDays(1).atStartOfDay(ZONE).toInstant()));

        List<ProductRevenue> byProduct = jdbc.query("""
                SELECT product_code, coalesce(sum(amount), 0) AS amount, count(*) AS cnt FROM payments
                WHERE status = 'PAID' AND paid_at >= :from AND paid_at < :to
                GROUP BY product_code ORDER BY amount DESC""",
            params, (rs, i) -> new ProductRevenue(rs.getString("product_code"), rs.getLong("amount"), rs.getLong("cnt")));
        long revenue = byProduct.stream().mapToLong(ProductRevenue::amount).sum();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (PaymentStatus s : PaymentStatus.values()) byStatus.put(s.name(), 0L);
        jdbc.query("SELECT status, count(*) AS cnt FROM payments WHERE created_at >= :from AND created_at < :to GROUP BY status",
            params, rs -> { byStatus.put(rs.getString("status"), rs.getLong("cnt")); });

        Map<String, Object> refunds = jdbc.queryForMap("""
            SELECT coalesce(sum(amount), 0) AS amount, count(*) AS cnt FROM payments
            WHERE status = 'REFUNDED' AND refunded_at >= :from AND refunded_at < :to""", params);

        Map<LocalDate, long[]> perDay = new HashMap<>();
        jdbc.query("""
                SELECT CAST(date_trunc('day', paid_at AT TIME ZONE 'Europe/Warsaw') AS date) AS day,
                       sum(amount) AS amount, count(*) AS cnt
                FROM payments WHERE status = 'PAID' AND paid_at >= :from AND paid_at < :to GROUP BY 1""",
            params, rs -> { perDay.put(rs.getDate("day").toLocalDate(), new long[]{rs.getLong("amount"), rs.getLong("cnt")}); });
        List<DailyRevenue> daily = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long[] v = perDay.getOrDefault(d, new long[]{0, 0});
            daily.add(new DailyRevenue(d, v[0], v[1]));
        }
        return new PaymentStats(start, end, revenue, byProduct, byStatus,
            ((Number) refunds.get("amount")).longValue(), ((Number) refunds.get("cnt")).longValue(), daily);
    }

    private static Specification<Payment> spec(PaymentFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.productCode() != null && !f.productCode().isBlank()) p.add(cb.equal(root.get("productCode"), f.productCode()));
            if (f.userId() != null) p.add(cb.equal(root.get("userId"), f.userId()));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(ZONE).toInstant()));
            if (f.to() != null) p.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String time(Instant instant) {
        return instant != null ? CSV_TIME.format(instant) : null;
    }

    /** Quotes when needed; a leading = + - @ is neutralised so Excel doesn't run it as a formula. */
    static String csv(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
