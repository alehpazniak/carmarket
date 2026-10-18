package com.carmarket.auction.service;

import com.carmarket.auction.config.W8ShippingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyExchangeServiceTest {

    private final AtomicInteger calls = new AtomicInteger();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);

    private CurrencyExchangeService service(HttpStatus status, String body) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build());
        });
        return new CurrencyExchangeService(builder, new W8ShippingConfig(), clock);
    }

    @Test
    void ratesComeFromOneDailyFetch() {
        CurrencyExchangeService service = service(HttpStatus.OK, "{\"base\":\"USD\",\"rates\":{\"PLN\":3.65,\"EUR\":0.86}}");

        assertThat(service.getUsdToPlnRate()).isEqualByComparingTo("3.6500");
        assertThat(service.getEurToPlnRate()).isEqualByComparingTo("4.2442"); // 3.65 / 0.86
        assertThat(service.getEurToUsdRate()).isEqualByComparingTo("1.1628"); // 1 / 0.86
        assertThat(service.lastRate()).hasValueSatisfying(last -> {
            assertThat(last.fallback()).isFalse();
            assertThat(last.fetchedAt()).isEqualTo(clock.instant());
        });
        assertThat(calls).hasValue(1);
    }

    @Test
    void failedFetchUsesTheFallbacks() {
        CurrencyExchangeService service = service(HttpStatus.SERVICE_UNAVAILABLE, "{}");

        assertThat(service.getUsdToPlnRate()).isEqualByComparingTo("4.00");
        assertThat(service.getEurToPlnRate()).isEqualByComparingTo("4.30");
        assertThat(service.getEurToUsdRate()).isEqualByComparingTo("1.0750"); // 4.30 / 4.00
        assertThat(service.lastRate()).hasValueSatisfying(last -> assertThat(last.fallback()).isTrue());
        assertThat(calls).hasValue(1);
    }
}
