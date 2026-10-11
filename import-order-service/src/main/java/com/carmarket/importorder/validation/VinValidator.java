package com.carmarket.importorder.validation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * VIN format (ISO 3779): 17 characters, digits and capital letters without I, O and Q.
 * The North-American check digit (position 9) is only reported as a warning: VINs from other
 * markets don't have to use it, and a lot page can carry a typo the dealer still has to accept.
 */
public final class VinValidator {

    private static final Pattern FORMAT = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    private static final int[] WEIGHTS = {8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2};

    private VinValidator() {
    }

    /** Uppercase, without surrounding or inner whitespace; null/blank becomes null. */
    public static String normalize(String vin) {
        if (vin == null) return null;
        String normalized = vin.replaceAll("\\s+", "").toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Expects a normalized VIN. */
    public static boolean isValidFormat(String vin) {
        return vin != null && FORMAT.matcher(vin).matches();
    }

    /** Expected check digit (0-9 or X) of a VIN with a valid format. */
    public static char expectedCheckDigit(String vin) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += transliterate(vin.charAt(i)) * WEIGHTS[i];
        }
        int remainder = sum % 11;
        return remainder == 10 ? 'X' : (char) ('0' + remainder);
    }

    /** A warning for a VIN whose position 9 doesn't match the North-American check digit. */
    public static Optional<String> checkDigitWarning(String vin) {
        char expected = expectedCheckDigit(vin);
        if (vin.charAt(8) == expected) return Optional.empty();
        return Optional.of("VIN check digit (position 9) is " + vin.charAt(8) + ", expected " + expected
            + " for a North-American VIN");
    }

    private static int transliterate(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        return switch (c) {
            case 'A', 'J' -> 1;
            case 'B', 'K', 'S' -> 2;
            case 'C', 'L', 'T' -> 3;
            case 'D', 'M', 'U' -> 4;
            case 'E', 'N', 'V' -> 5;
            case 'F', 'W' -> 6;
            case 'G', 'P', 'X' -> 7;
            case 'H', 'Y' -> 8;
            case 'R', 'Z' -> 9;
            default -> throw new IllegalArgumentException("Invalid VIN character: " + c);
        };
    }
}
