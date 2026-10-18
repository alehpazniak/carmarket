package com.carmarket.auction.service.shipping;

import com.carmarket.auction.service.shipping.W8LocationKey.ParsedLocation;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class W8LocationKeyTest {

    @Test
    void normalizeFollowsTheGeneratorRules() {
        assertThat(W8LocationKey.normalize("Ft. Pierce")).isEqualTo("FORT PIERCE");
        assertThat(W8LocationKey.normalize("St. Louis")).isEqualTo("SAINT LOUIS");
        assertThat(W8LocationKey.normalize("Chicago So")).isEqualTo("CHICAGO SOUTH");
        assertThat(W8LocationKey.normalize("Devil’s Bowl")).isEqualTo("DEVILS BOWL");
        assertThat(W8LocationKey.normalize("AUSTIN / WACO")).isEqualTo("AUSTIN WACO");
        assertThat(W8LocationKey.normalize("Baltimore–Washington")).isEqualTo("BALTIMORE WASHINGTON");
        assertThat(W8LocationKey.normalize("  ")).isNull();
        assertThat(W8LocationKey.normalize(null)).isNull();
    }

    @Test
    void apibaraDisplay() {
        assertThat(W8LocationKey.parse("Chicago South (IL)")).isEqualTo(new ParsedLocation("CHICAGO SOUTH", "IL", null));
        assertThat(W8LocationKey.parse("Sacramento (CA)")).isEqualTo(new ParsedLocation("SACRAMENTO", "CA", null));
    }

    @Test
    void copartOfficeName() {
        assertThat(W8LocationKey.parse("IL - CHICAGO SOUTH")).isEqualTo(new ParsedLocation("CHICAGO SOUTH", "IL", null));
    }

    @Test
    void w8Style() {
        assertThat(W8LocationKey.parse("CHICAGO SOUTH - IL")).isEqualTo(new ParsedLocation("CHICAGO SOUTH", "IL", null));
        assertThat(W8LocationKey.parse("CHICAGO SOUTH - IL (Heights Sublot)"))
            .isEqualTo(new ParsedLocation("CHICAGO SOUTH", "IL", "Heights Sublot"));
        assertThat(W8LocationKey.parse("CICERO-IN")).isEqualTo(new ParsedLocation("CICERO", "IN", null));
    }

    @Test
    void nameOnlyAndBareState() {
        assertThat(W8LocationKey.parse("KANSAS CITY EAST")).isEqualTo(new ParsedLocation("KANSAS CITY EAST", null, null));
        assertThat(W8LocationKey.parse("CA")).isEqualTo(new ParsedLocation(null, "CA", null));
        assertThat(W8LocationKey.parse("MANHEIM BALTIMORE-WASHINGTON"))
            .isEqualTo(new ParsedLocation("MANHEIM BALTIMORE WASHINGTON", null, null));
        assertThat(W8LocationKey.parse(null)).isEqualTo(new ParsedLocation(null, null, null));
    }

    /** Keeps Java and tools/w8/normalize_towing.py in sync: every seeded yard_key is normalize(yard). */
    @Test
    void everyCsvRowNormalizesToItsYardKey() throws Exception {
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/shipping/w8-towing-rates.csv"));
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> header = List.of(reader.readLine().split(",", -1));
            int yard = header.indexOf("yard");
            int key = header.indexOf("yard_key");
            int location = header.indexOf("location");
            int state = header.indexOf("state");
            List<String[]> rows = reader.lines().map(l -> l.split(",", -1)).toList();

            assertThat(rows).hasSize(647);
            for (String[] row : rows) {
                assertThat(W8LocationKey.normalize(row[yard])).as(row[yard]).isEqualTo(row[key]);
                // The display name parses back to the same yard and state
                ParsedLocation parsed = W8LocationKey.parse(row[location]);
                assertThat(parsed.yardKey()).as(row[location]).isEqualTo(row[key]);
                assertThat(parsed.state()).as(row[location]).isEqualTo(row[state].isEmpty() ? null : row[state]);
            }
        }
    }
}
