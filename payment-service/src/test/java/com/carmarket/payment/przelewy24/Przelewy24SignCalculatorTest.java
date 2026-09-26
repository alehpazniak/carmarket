package com.carmarket.payment.przelewy24;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class Przelewy24SignCalculatorTest {

    private final Przelewy24SignCalculator calculator = new Przelewy24SignCalculator();

    @Test
    void registerSignMatchesDocumentedJson() {
        // {"sessionId":"sessionId","merchantId":999999,"amount":1000,"currency":"PLN","crc":"crc"}
        assertThat(calculator.registerSign("sessionId", 999999, 1000, "PLN", "crc"))
            .isEqualTo("a34cf822f00b51d8d75e2d21334d6ffb5f2fac8408574f4cd7d4897f07ed61312244f9875e18ce63a4fbe2668d7ffc30");
    }

    @Test
    void verifySignMatchesDocumentedJson() {
        // {"sessionId":"sessionId","orderId":999999,"amount":1000,"currency":"PLN","crc":"crc"}
        assertThat(calculator.verifySign("sessionId", 999999L, 1000, "PLN", "crc"))
            .isEqualTo("3edcfa853ade37780fdcd00541b2c266117a7d35fab662ddc26498495c09b048e736f28424b616d2f1fbd882f6a47f0c");
    }

    @Test
    void doesNotEscapeSlashesOrUnicode() throws Exception {
        String expected = sha384("{\"sessionId\":\"żółć/ąę\",\"merchantId\":1,\"amount\":5,\"currency\":\"PLN\",\"crc\":\"k\"}");
        assertThat(calculator.registerSign("żółć/ąę", 1, 5, "PLN", "k")).isEqualTo(expected);
    }

    @Test
    void notificationSignUsesDocumentedFieldOrder() throws Exception {
        P24Notification n = new P24Notification(11, 22, "s-1", 1999, 1999, "PLN", 333L, 25, "p24-A1-B2", null);
        String expected = sha384("{\"merchantId\":11,\"posId\":22,\"sessionId\":\"s-1\",\"amount\":1999,"
            + "\"originAmount\":1999,\"currency\":\"PLN\",\"orderId\":333,\"methodId\":25,"
            + "\"statement\":\"p24-A1-B2\",\"crc\":\"secret\"}");
        assertThat(calculator.notificationSign(n, "secret")).isEqualTo(expected);
    }

    @Test
    void matchesIsCaseInsensitiveForReceivedSign() {
        String sign = calculator.registerSign("s", 1, 1, "PLN", "c");
        assertThat(calculator.matches(sign, sign.toUpperCase())).isTrue();
        assertThat(calculator.matches(sign, "deadbeef")).isFalse();
        assertThat(calculator.matches(sign, null)).isFalse();
    }

    private static String sha384(String s) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-384").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
