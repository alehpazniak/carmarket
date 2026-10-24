package com.carmarket.auction.sync;

import com.carmarket.auction.client.ApibaraQuota;
import com.carmarket.auction.client.ApibaraQuotaTracker;
import com.carmarket.auction.config.ApibaraSyncProperties;
import com.carmarket.auction.repository.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * How many Apibara requests the sync may still spend today: the period's budget left before today, spread evenly
 * over the days left in the period (today included), minus what today's runs already spent, and never more than
 * X-Subscription-Remaining − keep-remaining. Unspent days roll forward by themselves, since every day re-divides
 * what is left. The period is X-Subscription-Period-Start/End while now lies inside it, else the calendar month.
 * "Spent" is the sum of sync_runs.api_requests, so live lookups don't count against the sync's budget.
 */
@Component
@RequiredArgsConstructor
public class SyncBudget {

    private final ApibaraSyncProperties props;
    private final SyncRunRepository runs;
    private final ApibaraQuotaTracker quotaTracker;
    private final Clock clock;

    /**
     * @param dayAllowance requests left for today by the pacing alone
     * @param quotaCap     remaining − keep-remaining from the last quota headers; null when none were seen yet
     * @param available    what a scheduled run may spend now: the smaller of the two, never negative
     * @param periodLeft   monthly-budget − spent in the period, never negative
     * @param manualMax    the most an admin's manual run may spend: periodLeft, capped by quotaCap when known.
     *                     It may exceed today's share; the following days' pacing then lowers by itself.
     */
    public record Allowance(Instant periodStart, Instant periodEnd, long daysLeft, long spentInPeriod,
                            long spentToday, int dayAllowance, Integer quotaCap, int available,
                            int periodLeft, int manualMax) {
    }

    public Allowance today() {
        ZoneId zone = props.getZone();
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        ApibaraQuota quota = quotaTracker.latest().orElse(null);

        Instant periodStart;
        Instant periodEnd;
        if (quota != null && quota.periodStart() != null && quota.periodEnd() != null
            && !now.isBefore(quota.periodStart()) && now.isBefore(quota.periodEnd())) {
            periodStart = quota.periodStart();
            periodEnd = quota.periodEnd();
        } else {
            periodStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
            periodEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant();
        }
        // the period's last day is the one its end falls into (an end at midnight belongs to the day before)
        LocalDate lastDay = periodEnd.minusNanos(1).atZone(zone).toLocalDate();
        long daysLeft = Math.max(1, ChronoUnit.DAYS.between(today, lastDay) + 1);

        Instant todayStart = today.atStartOfDay(zone).toInstant();
        long spentInPeriod = runs.sumApiRequestsSince(periodStart);
        long spentToday = runs.sumApiRequestsSince(todayStart.isAfter(periodStart) ? todayStart : periodStart);
        long leftBeforeToday = props.getMonthlyBudget() - (spentInPeriod - spentToday);
        int dayAllowance = (int) Math.max(0, Math.floorDiv(Math.max(0, leftBeforeToday), daysLeft) - spentToday);

        Integer quotaCap = quota == null || quota.remaining() == null ? null
            : Math.max(0, quota.remaining() - props.getKeepRemaining());
        int available = quotaCap == null ? dayAllowance : Math.min(dayAllowance, quotaCap);
        int periodLeft = (int) Math.max(0, props.getMonthlyBudget() - spentInPeriod);
        int manualMax = quotaCap == null ? periodLeft : Math.min(periodLeft, quotaCap);
        return new Allowance(periodStart, periodEnd, daysLeft, spentInPeriod, spentToday, dayAllowance, quotaCap, available,
            periodLeft, manualMax);
    }

    /** The start of today in the sync's zone. */
    public ZonedDateTime startOfToday() {
        return clock.instant().atZone(props.getZone()).toLocalDate().atStartOfDay(props.getZone());
    }
}
