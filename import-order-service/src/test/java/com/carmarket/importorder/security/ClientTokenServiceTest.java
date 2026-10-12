package com.carmarket.importorder.security;

import com.carmarket.importorder.config.ImportOrderProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    private static ClientTokenService service(String secret, Duration ttl) {
        return new ClientTokenService(new ImportOrderProperties("http://localhost:5173", null, null,
            new ImportOrderProperties.Token(secret, ttl), null, 0));
    }

    @Test
    void roundTrip() {
        ClientTokenService tokens = service(SECRET, Duration.ofHours(12));
        UUID orderId = UUID.randomUUID();

        var issued = tokens.issue(orderId, 3);

        assertThat(tokens.parse(issued.token())).hasValueSatisfying(t -> {
            assertThat(t.orderId()).isEqualTo(orderId);
            assertThat(t.accessVersion()).isEqualTo(3);
            assertThat(t.tokenId()).isNotBlank();
        });
    }

    @Test
    void rejectsExpiredForeignAndGarbageTokens() {
        ClientTokenService tokens = service(SECRET, Duration.ofHours(12));
        String expired = service(SECRET, Duration.ofSeconds(-5)).issue(UUID.randomUUID(), 1).token();
        String foreign = service("another-secret-another-secret-another-1", Duration.ofHours(1))
            .issue(UUID.randomUUID(), 1).token();

        assertThat(tokens.parse(expired)).isEmpty();
        assertThat(tokens.parse(foreign)).isEmpty();
        assertThat(tokens.parse("not.a.token")).isEmpty();
        assertThat(tokens.parse(null)).isEmpty();
    }

    @Test
    void secretMustBeAtLeast32Bytes() {
        assertThatThrownBy(() -> service("too-short", Duration.ofHours(1))).isInstanceOf(IllegalStateException.class);
    }
}
