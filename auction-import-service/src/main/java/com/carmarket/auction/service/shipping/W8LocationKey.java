package com.carmarket.auction.service.shipping;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns an auction location, in any of the formats we see, into the {@code yard_key} + state used by
 * {@code w8_towing_rate}. {@link #normalize} must stay identical to {@code yard_key()} in
 * {@code tools/w8/normalize_towing.py}; {@code W8LocationKeyTest} checks it against every CSV row.
 */
public final class W8LocationKey {

    public record ParsedLocation(String yardKey, String state, String sublot) {}

    private static final Map<String, String> TOKENS = Map.of("FT", "FORT", "ST", "SAINT", "SO", "SOUTH");

    private static final Set<String> STATES = Set.of(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS", "KY",
        "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH",
        "OK", "OR", "PA", "PR", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY");

    /** Apibara {@code location.display}: {@code Chicago South (IL)}. */
    private static final Pattern NAME_PAREN_STATE = Pattern.compile("^(.+?)\\s*\\(\\s*([A-Za-z]{2})\\s*\\)$");
    /** W8: {@code CHICAGO SOUTH - IL}, {@code CHICAGO SOUTH - IL (Heights Sublot)}, {@code CICERO-IN}. */
    private static final Pattern NAME_DASH_STATE = Pattern.compile("^(.+?)\\s*-\\s*([A-Za-z]{2})\\s*(?:\\((.+)\\))?$");
    /** Copart office / {@code source_location_name}: {@code IL - CHICAGO SOUTH}. */
    private static final Pattern STATE_DASH_NAME = Pattern.compile("^([A-Za-z]{2})\\s*-\\s*(.+)$");

    private W8LocationKey() {
    }

    /**
     * Upper-case → en/em dash to '-' → drop apostrophes → every char not [A-Z0-9] to a space → split on
     * whitespace → FT→FORT, ST→SAINT, SO→SOUTH → join with single spaces. Null/blank → null.
     */
    public static String normalize(String value) {
        if (value == null) return null;
        String s = value.toUpperCase(Locale.ROOT)
            .replace('–', '-').replace('—', '-')
            .replace("'", "").replace("’", "")
            .replaceAll("[^A-Z0-9]", " ");
        String key = Arrays.stream(s.trim().split("\\s+"))
            .filter(t -> !t.isEmpty())
            .map(t -> TOKENS.getOrDefault(t, t))
            .collect(Collectors.joining(" "));
        return key.isEmpty() ? null : key;
    }

    /** Parses any known location format; parts that aren't there are null. Null/blank → all null. */
    public static ParsedLocation parse(String display) {
        if (display == null || display.isBlank()) return new ParsedLocation(null, null, null);
        String s = display.trim().replace('–', '-').replace('—', '-').replaceAll("\\s+", " ");

        String upper = s.toUpperCase(Locale.ROOT);
        if (STATES.contains(upper)) return new ParsedLocation(null, upper, null);

        Matcher m = NAME_PAREN_STATE.matcher(s);
        if (m.matches() && isState(m.group(2))) {
            return new ParsedLocation(normalize(m.group(1)), state(m.group(2)), null);
        }
        m = NAME_DASH_STATE.matcher(s);
        if (m.matches() && isState(m.group(2))) {
            String sublot = m.group(3) == null ? null : m.group(3).trim();
            return new ParsedLocation(normalize(m.group(1)), state(m.group(2)), sublot);
        }
        m = STATE_DASH_NAME.matcher(s);
        if (m.matches() && isState(m.group(1))) {
            return new ParsedLocation(normalize(m.group(2)), state(m.group(1)), null);
        }
        return new ParsedLocation(normalize(s), null, null);
    }

    private static boolean isState(String value) {
        return STATES.contains(state(value));
    }

    private static String state(String value) {
        return value.toUpperCase(Locale.ROOT);
    }
}
