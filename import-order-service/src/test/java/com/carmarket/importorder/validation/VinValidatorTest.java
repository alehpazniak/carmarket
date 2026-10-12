package com.carmarket.importorder.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VinValidatorTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(VinValidator.normalize(" 1m8gdm9a xkp042788 ")).isEqualTo("1M8GDM9AXKP042788");
        assertThat(VinValidator.normalize("   ")).isNull();
        assertThat(VinValidator.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1M8GDM9AXKP042788", "11111111111111111", "1HGCM82633A004352"})
    void acceptsValidFormat(String vin) {
        assertThat(VinValidator.isValidFormat(vin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1M8GDM9AXKP04278", "1M8GDM9AXKP0427888", "1M8GDM9AIKP042788", "1M8GDM9AOKP042788",
        "1M8GDM9AQKP042788", "1m8gdm9axkp042788", "1M8GDM9AXKP04278-"})
    void rejectsInvalidFormat(String vin) {
        assertThat(VinValidator.isValidFormat(vin)).isFalse();
    }

    @Test
    void checkDigitOfKnownVins() {
        // Textbook example with check digit X, and a Honda VIN with check digit 3
        assertThat(VinValidator.expectedCheckDigit("1M8GDM9AXKP042788")).isEqualTo('X');
        assertThat(VinValidator.checkDigitWarning("1M8GDM9AXKP042788")).isEmpty();
        assertThat(VinValidator.checkDigitWarning("1HGCM82633A004352")).isEmpty();
        assertThat(VinValidator.checkDigitWarning("11111111111111111")).isEmpty();
    }

    @Test
    void wrongCheckDigitIsOnlyAWarning() {
        assertThat(VinValidator.isValidFormat("1M8GDM9A1KP042788")).isTrue();
        assertThat(VinValidator.checkDigitWarning("1M8GDM9A1KP042788"))
            .hasValueSatisfying(w -> assertThat(w).contains("expected X"));
    }
}
