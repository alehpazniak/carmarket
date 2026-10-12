package com.carmarket.importorder.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Random client link codes and passwords. Passwords look like "k7mq-x2rd-p9fa": 12 characters from
 * an alphabet without look-alikes (0/o, 1/l/i), about 59 bits, typed easily on a phone. Dashes,
 * spaces and case don't matter when the client types it back ({@link #normalize}).
 */
@Component
public class ClientCredentialsGenerator {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_GROUPS = 3;
    private static final int GROUP_LENGTH = 4;

    private final SecureRandom random = new SecureRandom();

    /** 16 random bytes, base64url without padding (22 characters). */
    public String newPublicCode() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newPassword() {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < PASSWORD_GROUPS; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < GROUP_LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return sb.toString();
    }

    /** The form that is hashed and compared: lower case, without whitespace and dashes. */
    public static String normalize(String password) {
        return password == null ? "" : password.replaceAll("[\\s-]+", "").toLowerCase();
    }
}
