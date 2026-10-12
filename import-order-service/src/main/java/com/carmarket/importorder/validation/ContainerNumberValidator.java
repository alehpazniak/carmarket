package com.carmarket.importorder.validation;

import java.util.regex.Pattern;

/**
 * ISO 6346 container number: 3-letter owner code, category letter (U, J, Z or R), 6-digit serial
 * number and a check digit, e.g. CSQU3054383.
 */
public final class ContainerNumberValidator {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{3}[UJZR]\\d{6}\\d$");

    private ContainerNumberValidator() {
    }

    /** Uppercase, without whitespace or dashes ("csqu 305438-3" → "CSQU3054383"); blank becomes null. */
    public static String normalize(String number) {
        if (number == null) return null;
        String normalized = number.replaceAll("[\\s-]+", "").toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Format and check digit of a normalized number. */
    public static boolean isValid(String number) {
        if (number == null || !FORMAT.matcher(number).matches()) return false;
        return checkDigit(number.substring(0, 10)) == number.charAt(10) - '0';
    }

    /** Check digit of the first 10 characters (owner code, category, serial number). */
    static int checkDigit(String first10) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += value(first10.charAt(i)) << i; // weight 2^i
        }
        return sum % 11 % 10; // remainder 10 is written as 0
    }

    /** Letters count from A = 10 upwards, skipping multiples of 11 (11, 22, 33). */
    private static int value(char c) {
        if (Character.isDigit(c)) return c - '0';
        int v = 10 + (c - 'A');
        return v + (v - 1) / 10; // A=10, B=12 … K=21, L=23 … U=32, V=34 … Z=38
    }
}
