package com.carmarket.importorder.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gateway: /api/import-orders/admin/** — ADMIN only (SecurityConfig). Read-only figures for the dashboard. */
@RestController
@RequestMapping("/import-orders/admin")
@RequiredArgsConstructor
public class AdminImportOrderController {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final JdbcTemplate jdbc;

    public record DailyCount(LocalDate date, long count) {
    }

    /** byStatus: open (not archived) orders by current status, NONE = no status event yet. */
    public record ImportOrderStats(long total, long open, long archived, Map<String, Long> byStatus, long newLast30Days,
                                   long dealers, List<DailyCount> daily) {
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ImportOrderStats stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("""
                SELECT coalesce(status, 'NONE') AS status, count(*) AS cnt FROM import_orders
                WHERE archived_at IS NULL GROUP BY 1 ORDER BY 2 DESC""",
            rs -> { byStatus.put(rs.getString("status"), rs.getLong("cnt")); });
        long total = jdbc.queryForObject("SELECT count(*) FROM import_orders", Long.class);
        long archived = jdbc.queryForObject("SELECT count(*) FROM import_orders WHERE archived_at IS NOT NULL", Long.class);
        long new30 = jdbc.queryForObject("SELECT count(*) FROM import_orders WHERE created_at >= now() - interval '30 days'", Long.class);
        long dealers = jdbc.queryForObject("SELECT count(DISTINCT dealer_id) FROM import_orders", Long.class);

        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(29);
        Map<LocalDate, Long> perDay = new HashMap<>();
        jdbc.query("""
                SELECT CAST(date_trunc('day', created_at AT TIME ZONE 'Europe/Warsaw') AS date) AS day, count(*) AS cnt
                FROM import_orders WHERE created_at >= ? GROUP BY 1""",
            rs -> { perDay.put(rs.getDate("day").toLocalDate(), rs.getLong("cnt")); },
            java.sql.Timestamp.from(first.atStartOfDay(ZONE).toInstant()));
        List<DailyCount> daily = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) daily.add(new DailyCount(d, perDay.getOrDefault(d, 0L)));

        return new ImportOrderStats(total, total - archived, archived, byStatus, new30, dealers, daily);
    }
}
