package com.carmarket.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Counts only — admins never see message content or who talks to whom. */
@Service
@RequiredArgsConstructor
public class ChatStatsService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final JdbcTemplate jdbc;

    public record DailyCount(LocalDate date, long count) {
    }

    public record ChatStats(long conversations, long newConversationsLast7Days, long newConversationsLast30Days,
                            long messagesLast7Days, long messagesLast30Days, List<DailyCount> dailyMessages,
                            long blockedUsers) {
    }

    @Transactional(readOnly = true)
    public ChatStats stats() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(29);
        Map<LocalDate, Long> perDay = new HashMap<>();
        jdbc.query("""
                SELECT CAST(date_trunc('day', created_at AT TIME ZONE 'Europe/Warsaw') AS date) AS day, count(*) AS cnt
                FROM messages WHERE created_at >= ? GROUP BY 1""",
            rs -> { perDay.put(rs.getDate("day").toLocalDate(), rs.getLong("cnt")); },
            Timestamp.from(first.atStartOfDay(ZONE).toInstant()));
        List<DailyCount> daily = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) daily.add(new DailyCount(d, perDay.getOrDefault(d, 0L)));

        return new ChatStats(
            count("SELECT count(*) FROM conversations"),
            count("SELECT count(*) FROM conversations WHERE created_at >= now() - interval '7 days'"),
            count("SELECT count(*) FROM conversations WHERE created_at >= now() - interval '30 days'"),
            count("SELECT count(*) FROM messages WHERE created_at >= now() - interval '7 days'"),
            count("SELECT count(*) FROM messages WHERE created_at >= now() - interval '30 days'"),
            daily,
            count("SELECT count(*) FROM blocked_users"));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value != null ? value : 0;
    }
}
