package com.carmarket.payment.przelewy24;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Computes the P24 "sign" checksum: SHA-384 (hex) of a JSON object whose fields must appear
 * in exactly the documented order. Jackson escapes neither slashes nor non-ASCII characters,
 * which matches PHP json_encode with JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES used by P24.
 */
@Component
public class Przelewy24SignCalculator {

    private final ObjectMapper mapper = new ObjectMapper();

    /** {"sessionId","merchantId","amount","currency","crc"} */
    public String registerSign(String sessionId, int merchantId, int amount, String currency, String crc) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sessionId", sessionId);
        fields.put("merchantId", merchantId);
        fields.put("amount", amount);
        fields.put("currency", currency);
        fields.put("crc", crc);
        return sha384(fields);
    }

    /** {"sessionId","orderId","amount","currency","crc"} */
    public String verifySign(String sessionId, long orderId, int amount, String currency, String crc) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sessionId", sessionId);
        fields.put("orderId", orderId);
        fields.put("amount", amount);
        fields.put("currency", currency);
        fields.put("crc", crc);
        return sha384(fields);
    }

    /** {"merchantId","posId","sessionId","amount","originAmount","currency","orderId","methodId","statement","crc"} */
    public String notificationSign(P24Notification n, String crc) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("merchantId", n.merchantId());
        fields.put("posId", n.posId());
        fields.put("sessionId", n.sessionId());
        fields.put("amount", n.amount());
        fields.put("originAmount", n.originAmount());
        fields.put("currency", n.currency());
        fields.put("orderId", n.orderId());
        fields.put("methodId", n.methodId());
        fields.put("statement", n.statement());
        fields.put("crc", crc);
        return sha384(fields);
    }

    /** Constant-time comparison of a received sign against the expected one. */
    public boolean matches(String expected, String received) {
        if (received == null) return false;
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private String sha384(Map<String, Object> fields) {
        try {
            String json = mapper.writeValueAsString(fields);
            byte[] digest = MessageDigest.getInstance("SHA-384").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute Przelewy24 sign", e);
        }
    }
}
