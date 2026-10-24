package com.carmarket.auction.client;

import com.carmarket.auction.config.ParserTargetsConfig;
import com.carmarket.auction.repository.SyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The sync-only searchVehiclesPage: quota headers, cursor meta, typed exceptions. */
class ApibaraClientSyncTest {

    private static final String KEY = "secret-test-key";
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private final ParserTargetsConfig config = new ParserTargetsConfig();
    private final ApibaraQuotaTracker tracker = new ApibaraQuotaTracker(mock(SyncRunRepository.class));
    private final ApibaraClient client = new ApibaraClient(config, tracker, Clock.fixed(NOW, ZoneOffset.UTC));
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        config.setApiKey(KEY);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client.init(builder);
    }

    private static HttpHeaders quotaHeaders(int remaining) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Subscription-Limit", "100");
        headers.add("X-Subscription-Used", String.valueOf(100 - remaining));
        headers.add("X-Subscription-Remaining", String.valueOf(remaining));
        headers.add("X-Subscription-Period-Start", "2026-10-01T00:00:00+00:00");
        headers.add("X-Subscription-Period-End", "2026-10-31T23:59:59+00:00");
        headers.add("X-RateLimit-Remaining", "9");
        headers.add("X-Per-Page-Limit", "20");
        return headers;
    }

    private static MultiValueMap<String, String> params() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("platform", "copart");
        params.add("make", "BMW");
        params.add("per_page", "40");
        return params;
    }

    @Test
    void pageWithVehiclesCursorAndQuota() {
        server.expect(requestTo(startsWith("https://apibara.tech/api/v1/vehicle-auction/vehicles")))
            .andExpect(queryParam("make", "BMW"))
            .andExpect(header("X-API-Key", KEY))
            .andRespond(withSuccess("""
                {"data": [{"lot_number": "1", "make": "BMW"}, {"lot_number": "2", "make": "BMW"}],
                 "meta": {"per_page": 20, "next_cursor": "eyJhIjoxfQ", "prev_cursor": null, "has_more": true}}""",
                MediaType.APPLICATION_JSON).headers(quotaHeaders(42)));

        ApibaraVehiclesPage page = client.searchVehiclesPage(params());

        assertThat(page.vehicles()).extracting(v -> v.get("lot_number").asText()).containsExactly("1", "2");
        assertThat(page.nextCursor()).isEqualTo("eyJhIjoxfQ");
        assertThat(page.meta().perPage()).isEqualTo(20);
        assertThat(page.quota().remaining()).isEqualTo(42);
        assertThat(page.quota().periodStart()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(page.quota().observedAt()).isEqualTo(NOW);
        assertThat(tracker.latest()).contains(page.quota());
        assertThat(tracker.requestsSent()).isEqualTo(1);
        assertThat(client.effectivePerPage(40)).isEqualTo(20); // X-Per-Page-Limit
        server.verify();
    }

    @Test
    void totalIsReadWhenSent() {
        server.expect(requestTo(any(String.class)))
            .andRespond(withSuccess("{\"data\": [], \"meta\": {\"per_page\": 1, \"next_cursor\": null, \"total\": 18437}}",
                MediaType.APPLICATION_JSON));

        assertThat(client.searchVehiclesPage(params()).total()).isEqualTo(18437);
    }

    @Test
    void terminalChangesSendTerminalOneAndReadTheSequences() {
        server.expect(requestTo(startsWith("https://apibara.tech/api/v1/vehicle-auction/changes")))
            .andExpect(queryParam("terminal", "1")) // "true" gets a 422
            .andExpect(queryParam("after", "11818114"))
            .andExpect(queryParam("limit", "500"))
            .andRespond(withSuccess("""
                {"ok": true, "data": [{"sequence": 11818394, "event": "lot.sold"}],
                 "meta": {"after": 11818114, "next_cursor": 11818394, "has_more": true,
                          "latest_available_sequence": 34414178, "retention_days": 14}}""",
                MediaType.APPLICATION_JSON).headers(quotaHeaders(70)));

        ApibaraChangesPage page = client.terminalChangesPage(11818114, 500);

        assertThat(page.events()).hasSize(1);
        assertThat(page.nextCursor()).isEqualTo(11818394);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.latestAvailableSequence()).isEqualTo(34414178);
        assertThat(page.quota().remaining()).isEqualTo(70);
        assertThat(tracker.requestsSent()).isEqualTo(1);
    }

    @Test
    void anExpiredChangesCursorNamesTheLatestSequence() {
        server.expect(requestTo(any(String.class)))
            .andRespond(withStatus(HttpStatus.GONE).contentType(MediaType.APPLICATION_JSON).body("""
                {"ok": false, "error": "cursor_expired", "meta": {"latest_available_sequence": 34414178}}"""));

        assertThatThrownBy(() -> client.terminalChangesPage(5, 500))
            .isInstanceOfSatisfying(ApibaraCursorExpiredException.class,
                e -> assertThat(e.getLatestAvailableSequence()).isEqualTo(34414178));
    }

    @Test
    void aRejectedRequestIsItsOwnException() {
        server.expect(requestTo(any(String.class))).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> client.searchVehiclesPage(params()))
            .isInstanceOfSatisfying(ApibaraRejectedException.class, e -> assertThat(e.getStatus()).isEqualTo(422));
    }

    @Test
    void lastPageHasNoCursor() {
        server.expect(requestTo(any(String.class)))
            .andRespond(withSuccess("{\"data\": [], \"meta\": {\"per_page\": 20, \"next_cursor\": null}}",
                MediaType.APPLICATION_JSON));

        assertThat(client.searchVehiclesPage(params()).nextCursor()).isNull();
    }

    @Test
    void tooManyRequestsThrowsWithRetryAfterAndKeepsTheQuota() {
        HttpHeaders headers = quotaHeaders(0);
        headers.add(HttpHeaders.RETRY_AFTER, "3600");
        server.expect(requestTo(any(String.class)))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> client.searchVehiclesPage(params()))
            .isInstanceOfSatisfying(ApibaraQuotaExceededException.class, e -> {
                assertThat(e.getRetryAfter()).isEqualTo(Duration.ofHours(1));
                assertThat(e.getQuota().remaining()).isZero();
            });
        assertThat(tracker.retryNotBefore()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(tracker.latest().orElseThrow().remaining()).isZero();
        assertThat(tracker.requestsSent()).isEqualTo(1);
    }

    @Test
    void otherFailuresAreUnavailableAndNeverNameTheKey() {
        server.expect(requestTo(any(String.class))).andRespond(withServerError());
        assertThatThrownBy(() -> client.searchVehiclesPage(params()))
            .isInstanceOf(ApibaraUnavailableException.class)
            .hasMessageContaining("HTTP 500")
            .hasMessageNotContaining(KEY);

        server.reset();
        server.expect(requestTo(any(String.class)))
            .andRespond(withSuccess("{\"error\": \"nope\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.searchVehiclesPage(params())).isInstanceOf(ApibaraUnavailableException.class);

        server.reset();
        server.expect(requestTo(any(String.class)))
            .andRespond(request -> { throw new SocketTimeoutException("Read timed out"); });
        assertThatThrownBy(() -> client.searchVehiclesPage(params()))
            .isInstanceOf(ApibaraUnavailableException.class)
            .hasMessageNotContaining(KEY);
    }

    @Test
    void withoutAKeyNothingIsSent() {
        config.setApiKey("");
        assertThatThrownBy(() -> client.searchVehiclesPage(params())).isInstanceOf(ApibaraUnavailableException.class);
        assertThat(tracker.requestsSent()).isZero();
        server.verify();
    }

    @Test
    void perPageIsTheSmallerOfConfigAndTheLastLimit() {
        assertThat(client.effectivePerPage(50)).isEqualTo(50);
        client.rememberPerPageLimit("40");
        assertThat(client.effectivePerPage(50)).isEqualTo(40);
        assertThat(client.effectivePerPage(20)).isEqualTo(20);
        client.rememberPerPageLimit("garbage");
        client.rememberPerPageLimit(null);
        client.rememberPerPageLimit("0");
        assertThat(client.effectivePerPage(50)).isEqualTo(40);
    }

    @Test
    void retryAfterInSecondsOrAsHttpDate() {
        assertThat(ApibaraClient.retryAfter("120", NOW)).isEqualTo(Duration.ofMinutes(2));
        assertThat(ApibaraClient.retryAfter("Wed, 21 Oct 2026 11:00:00 GMT", NOW)).isEqualTo(Duration.ofHours(1));
        assertThat(ApibaraClient.retryAfter("soon", NOW)).isNull();
        assertThat(ApibaraClient.retryAfter(null, NOW)).isNull();
    }
}
