package com.carmarket.auction.service;

import com.carmarket.auction.config.W8ShippingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * USD and EUR rates in PLN from exchangerate-api.com (every rate relative to USD). The rates map is fetched
 * once per day and kept in memory, including a failed fetch, which serves the fallbacks until the next day.
 */
@Slf4j
@Service
public class CurrencyExchangeService {

    static final BigDecimal USD_PLN_FALLBACK = new BigDecimal("4.00");

    private final WebClient webClient;
    private final W8ShippingConfig w8Config;
    private final Clock clock;
    /** The last rate actually fetched (calls within the day are served from memory). */
    private volatile LastRate lastRate;
    private volatile DailyRates dailyRates;

    /** fallback = the API failed and the built-in 4.00 was used. */
    public record LastRate(BigDecimal rate, Instant fetchedAt, boolean fallback) {
    }

    /** {@code rates} = units per 1 USD (PLN, EUR, ...); null when the fetch failed. */
    private record DailyRates(LocalDate day, Instant fetchedAt, Map<String, Number> rates) {
    }

    public Optional<LastRate> lastRate() {
        return Optional.ofNullable(lastRate);
    }

    public CurrencyExchangeService(WebClient.Builder builder, W8ShippingConfig w8Config, Clock clock) {
        this.webClient = builder.baseUrl("https://api.exchangerate-api.com/v4/latest/USD").build();
        this.w8Config = w8Config;
        this.clock = clock;
    }

    public BigDecimal getUsdToPlnRate() {
        DailyRates daily = rates();
        Number pln = daily.rates() == null ? null : daily.rates().get("PLN");
        boolean fallback = pln == null;
        BigDecimal rate = fallback ? USD_PLN_FALLBACK : BigDecimal.valueOf(pln.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        lastRate = new LastRate(rate, daily.fetchedAt(), fallback);
        return rate;
    }

    /** PLN per EUR (= PLN / EUR in the USD-based table); fallback {@code import.w8.eur-pln-fallback}. */
    public BigDecimal getEurToPlnRate() {
        Map<String, Number> rates = rates().rates();
        if (rates != null && rates.get("PLN") != null && rates.get("EUR") != null && rates.get("EUR").doubleValue() > 0) {
            return BigDecimal.valueOf(rates.get("PLN").doubleValue())
                .divide(BigDecimal.valueOf(rates.get("EUR").doubleValue()), 4, RoundingMode.HALF_UP);
        }
        return w8Config.getEurPlnFallback();
    }

    /** USD per EUR (= 1 / EUR); without a fetched rate it's derived from the two PLN fallbacks. */
    public BigDecimal getEurToUsdRate() {
        Map<String, Number> rates = rates().rates();
        if (rates != null && rates.get("EUR") != null && rates.get("EUR").doubleValue() > 0) {
            return BigDecimal.ONE.divide(BigDecimal.valueOf(rates.get("EUR").doubleValue()), 4, RoundingMode.HALF_UP);
        }
        return getEurToPlnRate().divide(getUsdToPlnRate(), 4, RoundingMode.HALF_UP);
    }

    private DailyRates rates() {
        LocalDate today = LocalDate.now(clock);
        DailyRates daily = dailyRates;
        if (daily != null && daily.day().equals(today)) return daily;
        synchronized (this) {
            if (dailyRates == null || !dailyRates.day().equals(today)) {
                dailyRates = new DailyRates(today, Instant.now(clock), fetch());
            }
            return dailyRates;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Number> fetch() {
        try {
            Map<String, Object> response = webClient.get().retrieve().bodyToMono(Map.class).block();
            if (response != null && response.get("rates") instanceof Map<?, ?> rates) {
                return (Map<String, Number>) rates;
            }
            log.warn("Exchange rate response has no rates, using fallbacks");
        } catch (Exception e) {
            log.warn("Failed to fetch exchange rates, using fallbacks", e);
        }
        return null;
    }
}
