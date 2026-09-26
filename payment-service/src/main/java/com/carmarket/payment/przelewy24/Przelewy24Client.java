package com.carmarket.payment.przelewy24;

import com.carmarket.payment.przelewy24.P24Requests.Refund;
import com.carmarket.payment.przelewy24.P24Requests.RefundItem;
import com.carmarket.payment.przelewy24.P24Requests.RefundResponse;
import com.carmarket.payment.przelewy24.P24Requests.RefundResult;
import com.carmarket.payment.przelewy24.P24Requests.Register;
import com.carmarket.payment.przelewy24.P24Requests.RegisterResponse;
import com.carmarket.payment.przelewy24.P24Requests.TestAccessResponse;
import com.carmarket.payment.przelewy24.P24Requests.TransactionDetails;
import com.carmarket.payment.przelewy24.P24Requests.TransactionResponse;
import com.carmarket.payment.przelewy24.P24Requests.Verify;
import com.carmarket.payment.przelewy24.P24Requests.VerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thin client over the Przelewy24 REST API v1. Authenticates with HTTP Basic
 * (login = posId, password = API key / "klucz do raportów").
 */
@Slf4j
@Component
public class Przelewy24Client {

    private final Przelewy24Properties props;
    private final Przelewy24SignCalculator signCalculator;
    private final RestClient restClient;

    public Przelewy24Client(Przelewy24Properties props,
                            Przelewy24SignCalculator signCalculator,
                            RestClient.Builder builder) {
        this.props = props;
        this.signCalculator = signCalculator;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(20_000);
        this.restClient = builder
            .baseUrl(props.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /** Registers a transaction and returns the token for the /trnRequest/{token} payment page. */
    public String registerTransaction(String sessionId, int amount, String currency, String description,
                                      String email, String urlReturn) {
        Register body = new Register(
            props.getMerchantId(),
            props.effectivePosId(),
            sessionId,
            amount,
            currency,
            description,
            email,
            "PL",
            props.getLanguage(),
            urlReturn,
            props.getUrlStatus(),
            props.getTimeLimit(),
            null,
            signCalculator.registerSign(sessionId, props.getMerchantId(), amount, currency, props.getCrc())
        );
        RegisterResponse response = call("transaction/register", () -> restClient.post()
            .uri("/api/v1/transaction/register")
            .header(HttpHeaders.AUTHORIZATION, basicAuth())
            .body(body)
            .retrieve()
            .body(RegisterResponse.class));
        if (response == null || response.data() == null || response.data().token() == null) {
            throw new Przelewy24Exception("transaction/register returned no token");
        }
        return response.data().token();
    }

    /** Confirms receipt of the payment. Until this succeeds P24 does not settle the money. */
    public void verifyTransaction(String sessionId, long orderId, int amount, String currency) {
        Verify body = new Verify(
            props.getMerchantId(),
            props.effectivePosId(),
            sessionId,
            amount,
            currency,
            orderId,
            signCalculator.verifySign(sessionId, orderId, amount, currency, props.getCrc())
        );
        VerifyResponse response = call("transaction/verify", () -> restClient.put()
            .uri("/api/v1/transaction/verify")
            .header(HttpHeaders.AUTHORIZATION, basicAuth())
            .body(body)
            .retrieve()
            .body(VerifyResponse.class));
        if (response == null || response.data() == null || !"success".equalsIgnoreCase(response.data().status())) {
            throw new Przelewy24Exception("transaction/verify did not return success for session " + sessionId);
        }
    }

    /** Transaction state on the P24 side, or empty if P24 does not know the session. */
    public Optional<TransactionDetails> getTransaction(String sessionId) {
        try {
            TransactionResponse response = call("transaction/by/sessionId", () -> restClient.get()
                .uri("/api/v1/transaction/by/sessionId/{sessionId}", sessionId)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .retrieve()
                .body(TransactionResponse.class));
            return Optional.ofNullable(response).map(TransactionResponse::data);
        } catch (Przelewy24Exception e) {
            if (e.getCause() instanceof HttpClientErrorException.NotFound) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Requests a refund. P24 processes it asynchronously; returns P24's per-item result. */
    public RefundResult refund(String requestId, String refundsUuid, long orderId, String sessionId,
                               int amount, String description) {
        Refund body = new Refund(
            requestId,
            List.of(new RefundItem(orderId, sessionId, amount, description)),
            refundsUuid,
            null
        );
        RefundResponse response = call("transaction/refund", () -> restClient.post()
            .uri("/api/v1/transaction/refund")
            .header(HttpHeaders.AUTHORIZATION, basicAuth())
            .body(body)
            .retrieve()
            .body(RefundResponse.class));
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new Przelewy24Exception("transaction/refund returned no result");
        }
        RefundResult result = response.data().get(0);
        if (!Boolean.TRUE.equals(result.status())) {
            throw new Przelewy24Exception("Refund rejected by Przelewy24: " + result.message());
        }
        return result;
    }

    /** GET /api/v1/testAccess — true when the credentials are accepted. */
    public boolean testAccess() {
        try {
            TestAccessResponse response = restClient.get()
                .uri("/api/v1/testAccess")
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .retrieve()
                .body(TestAccessResponse.class);
            return response != null && Boolean.TRUE.equals(response.data());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                return false;
            }
            throw new Przelewy24Exception("testAccess failed: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new Przelewy24Exception("Przelewy24 unreachable: " + e.getMessage(), e);
        }
    }

    private <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            log.warn("Przelewy24 {} failed: {} {}", operation, e.getStatusCode(), e.getResponseBodyAsString());
            throw new Przelewy24Exception("Przelewy24 " + operation + " failed with " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.warn("Przelewy24 {} unreachable: {}", operation, e.getMessage());
            throw new Przelewy24Exception("Przelewy24 unreachable: " + e.getMessage(), e);
        }
    }

    private String basicAuth() {
        String credentials = props.effectivePosId() + ":" + props.getApiKey();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
