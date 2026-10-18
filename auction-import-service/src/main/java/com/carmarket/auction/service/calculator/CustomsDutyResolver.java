package com.carmarket.auction.service.calculator;

import com.carmarket.auction.config.ImportRatesConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * EU customs duty ("cło") rate for an imported passenger car, by its country of manufacture.
 * <p>
 * Since 1 July 2026, cars made in the US pay {@code us-origin-duty-rate} (0%) under Regulation (EU) 2026/1455,
 * until {@code us-origin-preference-until} (31 December 2029); after that date they are back to the standard
 * rate. Cars made anywhere else pay {@code customs-duty-rate} (10%), even when bought at a US auction.
 * <p>
 * The country of manufacture comes from the first VIN character (WMI region): 1, 4 and 5 are the USA.
 * Copart masks only the last 6 characters, so a masked VIN still has it. A missing or malformed VIN gets the
 * standard rate, so the estimate never understates the buyer's cost.
 */
@Component
@RequiredArgsConstructor
public class CustomsDutyResolver {

    /** 17 characters, no I/O/Q; the last 6 may be masked with '*'. */
    private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{11}[A-HJ-NPR-Z0-9*]{6}");

    private final ImportRatesConfig rates;
    private final Clock clock;

    public BigDecimal resolve(String vin) {
        if (isUsBuilt(vin) && !LocalDate.now(clock).isAfter(rates.getUsOriginPreferenceUntil())) {
            return rates.getUsOriginDutyRate();
        }
        return rates.getCustomsDutyRate();
    }

    static boolean isUsBuilt(String vin) {
        if (!isVin(vin)) return false;
        char region = Character.toUpperCase(vin.charAt(0));
        return region == '1' || region == '4' || region == '5';
    }

    public static boolean isVin(String value) {
        return value != null && VIN.matcher(value.toUpperCase(Locale.ROOT)).matches();
    }
}
