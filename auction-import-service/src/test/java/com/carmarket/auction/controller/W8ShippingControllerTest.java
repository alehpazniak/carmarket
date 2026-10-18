package com.carmarket.auction.controller;

import com.carmarket.auction.config.SecurityConfig;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.security.GatewaySignatureFilter;
import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.PolandZone;
import com.carmarket.auction.service.shipping.W8QuoteRequest;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService.PortPrice;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService.W8Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(W8ShippingController.class)
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=" + W8ShippingControllerTest.SECRET)
class W8ShippingControllerTest {

    static final String SECRET = "test-gateway-secret";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private W8ShippingQuoteService quoteService;

    /** Unlike the Apibara lookups, the W8 calculator is for logged-in users only. */
    @Test
    void anonymousCallsAreRejected() throws Exception {
        mvc.perform(get("/api/auctions/shipping/w8/locations").param("q", "chic")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auctions/shipping/w8/quote").param("location", "Chicago South (IL)")).andExpect(status().isForbidden());
        verify(quoteService, never()).locations(any(), any());
        verify(quoteService, never()).quote(any());
    }

    @Test
    void locationsNeedTwoCharactersAndAKnownAuction() throws Exception {
        when(quoteService.locations(W8Auction.COPART, "chic")).thenReturn(List.of(new W8Location(W8Auction.COPART,
            "CHICAGO SOUTH - IL", "IL", List.of(new PortPrice(W8Port.NEW_YORK, new BigDecimal("400"))))));

        mvc.perform(get("/api/auctions/shipping/w8/locations").param("auction", "COPART").param("q", "chic").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].location").value("CHICAGO SOUTH - IL"))
            .andExpect(jsonPath("$[0].ports[0].port").value("NEW_YORK"))
            .andExpect(jsonPath("$[0].ports[0].towingUsd").value(400));
        mvc.perform(get("/api/auctions/shipping/w8/locations").param("q", "c").with(user())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/auctions/shipping/w8/locations").param("auction", "ebay").param("q", "chic").with(user()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void quoteIs404WithoutAMatch() throws Exception {
        W8ShippingQuote quote = mock(W8ShippingQuote.class);
        when(quoteService.quote(any())).thenReturn(Optional.empty());
        when(quoteService.quote(new W8QuoteRequest(W8Auction.IAAI, "Greensboro (NC)", true, PolandZone.C, LengthClass.OVER_5M)))
            .thenReturn(Optional.of(quote));

        mvc.perform(get("/api/auctions/shipping/w8/quote").with(user())
                .param("auction", "iaai").param("location", "Greensboro (NC)")
                .param("suv", "true").param("zone", "C").param("length", "OVER_5M"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/auctions/shipping/w8/quote").with(user()).param("location", "Honolulu (HI)"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/auctions/shipping/w8/quote").with(user()).param("location", "x").param("zone", "D"))
            .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor user() {
        return request -> {
            String userId = UUID.randomUUID().toString();
            try {
                long ts = System.currentTimeMillis();
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                String sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((userId + ":" + ts).getBytes(StandardCharsets.UTF_8)));
                request.addHeader("X-User-Id", userId);
                request.addHeader("X-User-Roles", "USER");
                request.addHeader("X-Gateway-Signature", ts + ":" + sig);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return request;
        };
    }
}
