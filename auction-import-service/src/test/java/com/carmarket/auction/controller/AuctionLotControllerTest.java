package com.carmarket.auction.controller;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.config.SecurityConfig;
import com.carmarket.auction.dto.LotFacetsResponse;
import com.carmarket.auction.repository.AuctionLotRepository;
import com.carmarket.auction.security.GatewaySignatureFilter;
import com.carmarket.auction.service.AuctionLotService;
import com.carmarket.auction.service.LotLookupService;
import com.carmarket.auction.dto.LotLookupResponse;
import com.carmarket.auction.service.UsaShippingRateService;
import com.carmarket.auction.service.calculator.ImportCostCalculator;
import com.carmarket.auction.service.calculator.MaxBidCalculator;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requests here carry no gateway signature, i.e. they are anonymous, like a logged-out visitor on /aukcje. */
@WebMvcTest(AuctionLotController.class)
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=test-gateway-secret")
class AuctionLotControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private AuctionLotRepository lotRepository;
    @MockBean
    private AuctionLotService lotService;
    @MockBean
    private ImportCostCalculator costCalculator;
    @MockBean
    private MaxBidCalculator maxBidCalculator;
    @MockBean
    private W8ShippingQuoteService w8QuoteService;
    @MockBean
    private ApibaraClient apibaraClient;
    @MockBean
    private UsaShippingRateService shippingRateService;
    @MockBean
    private LotLookupService lotLookup;

    @Test
    void facetsAreAnonymousAndDontHitLotById() throws Exception {
        when(lotRepository.findFacetRows()).thenReturn(List.of(
            new LotFacetsResponse.Row("TOYOTA", "RAV4", 7L), new LotFacetsResponse.Row("TOYOTA", "CAMRY", 5L)));

        mvc.perform(get("/api/auctions/lots/facets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.makes[0].make").value("TOYOTA"))
            .andExpect(jsonPath("$.makes[0].count").value(12))
            .andExpect(jsonPath("$.makes[0].models[0].model").value("CAMRY"));

        verify(lotRepository, never()).findById(any());
    }

    /** anonymous like the rest of GET /lots/**, and the literal path wins over /lots/{id} (a UUID) */
    @Test
    void lookupIsAnonymousAndWinsOverLotById() throws Exception {
        UUID id = UUID.fromString("28d85e9b-9b15-4b91-a75b-5655c3eb4257");
        when(lotLookup.lookup("61747696")).thenReturn(new LotLookupResponse(LotLookupResponse.Kind.LOT_NUMBER, List.of(id), "61747696"));

        mvc.perform(get("/api/auctions/lots/lookup").param("q", "61747696"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("LOT_NUMBER"))
            .andExpect(jsonPath("$.ids[0]").value(id.toString()))
            .andExpect(jsonPath("$.identifier").value("61747696"));

        verify(lotRepository, never()).findById(any());
        verify(apibaraClient, never()).isConfigured(); // no Apibara request
    }

    @Test
    void lookupRejectsAnOverlongQuery() throws Exception {
        mvc.perform(get("/api/auctions/lots/lookup").param("q", "x".repeat(501))).andExpect(status().isBadRequest());
        verify(lotLookup, never()).lookup(any());
    }

    @Test
    void lotsNormalizeMakeAndModelAndTreatBlankAsAny() throws Exception {
        when(lotRepository.searchLiveLots(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/auctions/lots").param("make", " toyota ").param("model", "rav4"))
            .andExpect(status().isOk());
        verify(lotRepository).searchLiveLots(eq("TOYOTA"), eq("RAV4"), isNull(), isNull(), isNull(), isNull(), isNull(), any());

        mvc.perform(get("/api/auctions/lots").param("make", "MAZDA").param("model", "  "))
            .andExpect(status().isOk());
        verify(lotRepository).searchLiveLots(eq("MAZDA"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }
}
