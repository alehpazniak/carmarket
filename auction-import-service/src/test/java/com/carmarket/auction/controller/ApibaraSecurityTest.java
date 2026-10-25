package com.carmarket.auction.controller;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraQuotaExceededException;
import com.carmarket.auction.client.ApibaraUnavailableException;
import com.carmarket.auction.config.SecurityConfig;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.security.GatewaySignatureFilter;
import com.carmarket.auction.service.LiveVehicleLookup;
import com.carmarket.auction.service.UsaShippingRateService;
import com.carmarket.auction.service.calculator.MaxBidCalculator;
import com.carmarket.auction.service.calculator.MaxBidResult;
import com.carmarket.auction.service.shipping.W8QuoteRequest;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Apibara live lookups are open to anonymous users (the gateway rate-limits them); the account usage
 * and the image proxy are not. Requests here carry no gateway signature, i.e. they are anonymous.
 */
@WebMvcTest(ApibaraController.class)
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=test-gateway-secret")
class ApibaraSecurityTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ApibaraClient client;
    @MockBean
    private MaxBidCalculator maxBidCalculator;
    @MockBean
    private UsaShippingRateService shippingRateService;
    @MockBean
    private LiveVehicleLookup liveLookup;
    @MockBean
    private W8ShippingQuoteService w8QuoteService;

    @Test
    void anonymousLookupsReachTheController() throws Exception {
        // The mocked client returns null → 502, the mocked lookup nothing → 404: the requests got past security
        when(client.isConfigured()).thenReturn(true);
        when(liveLookup.vehicle(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/auctions/apibara/vehicles").param("make", "BMW")).andExpect(status().isBadGateway());
        mvc.perform(get("/api/auctions/apibara/vehicles/{vin}", "1hgcm82633a004352")).andExpect(status().isNotFound());
        verify(client).searchVehicles(any());
        verify(liveLookup).vehicle("1hgcm82633a004352");
    }

    private static com.carmarket.auction.client.ApibaraQuota quota(Integer remaining, Integer rateLimitRemaining) {
        return new com.carmarket.auction.client.ApibaraQuota(100, null, remaining, null, null, rateLimitRemaining, null,
            java.time.Instant.parse("2026-10-25T10:00:00Z"));
    }

    /** Apibara's monthly quota is used up (X-Subscription-Remaining 0): 429, the reason and Apibara's Retry-After */
    @Test
    void anExhaustedQuotaSaysSo() throws Exception {
        when(client.isConfigured()).thenReturn(true);
        when(liveLookup.resolve("https://www.copart.com/lot/61747696")).thenThrow(
            new ApibaraQuotaExceededException(java.time.Duration.ofHours(1), quota(0, 9)));

        mvc.perform(get("/api/auctions/apibara/vehicles/url-to-details").param("url", "https://www.copart.com/lot/61747696"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "3600"))
            .andExpect(jsonPath("$.reason").value("QUOTA_EXHAUSTED"));
    }

    /** a 429 with quota left (or without the header) is the short-term limit; no Retry-After when Apibara sent none */
    @Test
    void aShortTermLimitIsRateLimited() throws Exception {
        when(client.isConfigured()).thenReturn(true);
        when(liveLookup.vehicle("61747696")).thenThrow(new ApibaraQuotaExceededException(java.time.Duration.ofSeconds(30), quota(57, 0)));
        when(liveLookup.vehicle("61747697")).thenThrow(new ApibaraQuotaExceededException(null, null));

        mvc.perform(get("/api/auctions/apibara/vehicles/{id}", "61747696"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.reason").value("RATE_LIMITED"));
        mvc.perform(get("/api/auctions/apibara/vehicles/{id}", "61747697"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().doesNotExist("Retry-After"))
            .andExpect(jsonPath("$.reason").value("RATE_LIMITED"));
    }

    /** any other failure: 502 with UNAVAILABLE; no API key stays a bare 503 */
    @Test
    void otherFailuresAreUnavailable() throws Exception {
        when(client.isConfigured()).thenReturn(true);
        when(liveLookup.vehicle("61747696")).thenThrow(new ApibaraUnavailableException("Apibara answered HTTP 500"));

        mvc.perform(get("/api/auctions/apibara/vehicles/{id}", "61747696"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.reason").value("UNAVAILABLE"));

        when(client.isConfigured()).thenReturn(false);
        mvc.perform(get("/api/auctions/apibara/vehicles/{id}", "61747696")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void anonymousMaxBidIsNotForbidden() throws Exception {
        when(maxBidCalculator.calculate(any())).thenReturn(MaxBidResult.builder().build());
        mvc.perform(post("/api/auctions/apibara/vehicles/{vin}/max-bid", "1hgcm82633a004352")
                .contentType("application/json").content("{}"))
            .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .isNotIn(401, 403));
    }

    /** W8's table prices the yard for free, so the quota-metered Apibara shipping lookup is skipped. */
    @Test
    void maxBidWithAW8QuoteSkipsTheApibaraShippingLookup() throws Exception {
        W8ShippingQuote quote = mock(W8ShippingQuote.class);
        when(w8QuoteService.quote(any())).thenReturn(Optional.of(quote));
        when(maxBidCalculator.calculate(any())).thenReturn(MaxBidResult.builder().build());

        mvc.perform(post("/api/auctions/apibara/vehicles/{vin}/max-bid", "1hgcm82633a004352")
                .contentType("application/json")
                .content("{\"budgetPln\":100000,\"auction\":\"copart\",\"auctionLocation\":\"Somerville (NJ)\"}"))
            .andExpect(status().isOk());

        verify(client, never()).getVehicleShipping(any(), any());
        verify(w8QuoteService).quote(new W8QuoteRequest(W8Auction.COPART, "Somerville (NJ)", null, null, null));
        verify(maxBidCalculator).calculate(argThat(input -> input.getW8Quote() == quote && input.getShippingCostUsd() == null));
    }

    @Test
    void maxBidRejectsAnUnknownAuctionOrAnOverlongLocation() throws Exception {
        mvc.perform(post("/api/auctions/apibara/vehicles/{vin}/max-bid", "1hgcm82633a004352")
                .contentType("application/json").content("{\"budgetPln\":100000,\"auction\":\"ebay\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auctions/apibara/vehicles/{vin}/max-bid", "1hgcm82633a004352")
                .contentType("application/json")
                .content("{\"budgetPln\":100000,\"auctionLocation\":\"" + "x".repeat(151) + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auctions/apibara/vehicles/{vin}/max-bid", "1hgcm82633a004352")
                .contentType("application/json").content("{\"budgetPln\":100000,\"polandZone\":\"D\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void usageAndImageProxyStayForLoggedInUsers() throws Exception {
        mvc.perform(get("/api/auctions/apibara/usage")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auctions/apibara/image-proxy").param("url", "https://example.com/a.jpg"))
            .andExpect(status().isForbidden());
        verify(client, never()).getUsage();
        verify(client, never()).proxyImage(any());
    }
}
