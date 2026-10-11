package com.carmarket.importorder.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerNumberValidatorTest {

    @Test
    void normalizes() {
        assertThat(ContainerNumberValidator.normalize(" csqu 305438-3 ")).isEqualTo("CSQU3054383");
        assertThat(ContainerNumberValidator.normalize("  ")).isNull();
    }

    @ParameterizedTest
    // ISO 6346 reference example, plus other numbers with a correct check digit
    @ValueSource(strings = {"CSQU3054383", "MSKU9070323", "TGHU7539007", "MSCU1234566"})
    void acceptsValidNumbers(String number) {
        assertThat(ContainerNumberValidator.isValid(number)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CSQU3054384",  // wrong check digit
        "CSQX3054383",  // category must be U, J, Z or R
        "CSQU305438",   // too short
        "CSQU30543833", // too long
        "C5QU3054383",  // digit in the owner code
        "csqu3054383"   // not normalized
    })
    void rejectsInvalidNumbers(String number) {
        assertThat(ContainerNumberValidator.isValid(number)).isFalse();
    }

    @Test
    void remainderTenIsWrittenAsZero() {
        // Sum of this prefix is ≡ 10 (mod 11); the check digit is then 0
        String prefix = findPrefixWithRemainderTen();
        assertThat(ContainerNumberValidator.checkDigit(prefix)).isZero();
        assertThat(ContainerNumberValidator.isValid(prefix + "0")).isTrue();
    }

    private static String findPrefixWithRemainderTen() {
        for (int serial = 0; serial < 1_000_000; serial++) {
            String prefix = "ABCU" + String.format("%06d", serial);
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                char c = prefix.charAt(i);
                int v = Character.isDigit(c) ? c - '0' : 10 + (c - 'A') + (9 + (c - 'A')) / 10;
                sum += v << i;
            }
            if (sum % 11 == 10) return prefix;
        }
        throw new AssertionError("no prefix found");
    }
}
