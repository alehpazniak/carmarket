package com.carmarket.auction.controller;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.config.SecurityConfig;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.security.GatewaySignatureFilter;
import com.carmarket.auction.service.ApibaraVehicleResolver;
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
    private ApibaraVehicleResolver vehicleResolver;
    @MockBean
    private W8ShippingQuoteService w8QuoteService;

    @Test
    void anonymousLookupsReachTheController() throws Exception {
        // The mocked client returns null → 502, i.e. the request got past security
        mvc.perform(get("/api/auctions/apibara/vehicles").param("make", "BMW")).andExpect(status().isBadGateway());
        mvc.perform(get("/api/auctions/apibara/vehicles/{vin}", "1hgcm82633a004352")).andExpect(status().isBadGateway());
        verify(client).searchVehicles(any());
        verify(client).getVehicle("1hgcm82633a004352");
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
