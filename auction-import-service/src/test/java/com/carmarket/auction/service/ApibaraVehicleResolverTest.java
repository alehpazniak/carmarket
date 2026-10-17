package com.carmarket.auction.service;

import com.carmarket.auction.client.ApibaraClient;
import com.carmarket.auction.client.ApibaraVehicleDetailResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApibaraVehicleResolverTest {

    private static final String COPART_URL =
        "https://www.copart.com/lot/61747696/salvage-2021-mazda-cx-5-grand-touring-il-chicago-south";

    private final ApibaraClient client = mock(ApibaraClient.class);
    private final ApibaraVehicleResolver resolver = new ApibaraVehicleResolver(client);

    /** A real urltodetails response must map onto the same record as GET /vehicles/{slugVin}. */
    @Test
    void urltodetailsResponseDeserializes() throws Exception {
        ApibaraVehicleDetailResponse response = fixture();

        assertThat(response.data().vin()).isEqualTo("JM3KFBDM6M1463543");
        assertThat(response.data().lotNumber()).isEqualTo("61747696");
        assertThat(response.data().platform()).isEqualTo("copart");
        assertThat(response.data().make()).isEqualTo("MAZDA");
        assertThat(response.data().year()).isEqualTo(2021);
        assertThat(response.data().location().display()).isEqualTo("Chicago South (IL)");
        assertThat(response.data().saleDocument().name()).isEqualTo("SALVAGE CERTIFICATE");
        assertThat(response.data().auction().auctionAt()).startsWith("2026-09-09");
        assertThat(response.data().media().items()).isNotEmpty();
    }

    @Test
    void urlResolvedInOneCall() throws Exception {
        ApibaraVehicleDetailResponse vehicle = fixture();
        when(client.resolveFromUrl(COPART_URL)).thenReturn(vehicle);

        assertThat(resolver.resolve(" " + COPART_URL + " ")).contains(vehicle);
        verify(client, never()).getVehicle(anyString());
    }

    @Test
    void fallsBackToCopartLotNumberWhenUrlIsNotMatched() throws Exception {
        ApibaraVehicleDetailResponse vehicle = fixture();
        when(client.resolveFromUrl(COPART_URL)).thenReturn(null);
        when(client.getVehicle("61747696")).thenReturn(vehicle);

        assertThat(resolver.resolve(COPART_URL)).contains(vehicle);
    }

    @Test
    void bareLotNumberOrVinIsLookedUpDirectly() throws Exception {
        ApibaraVehicleDetailResponse vehicle = fixture();
        when(client.getVehicle("61747696")).thenReturn(vehicle);
        when(client.getVehicle("JM3KFBDM6M1463543")).thenReturn(vehicle);

        assertThat(resolver.resolve("61747696")).contains(vehicle);
        assertThat(resolver.resolve("jm3kfbdm6m1463543")).contains(vehicle);
        verify(client, never()).resolveFromUrl(anyString());
    }

    @Test
    void nothingFound() {
        assertThat(resolver.resolve("https://www.iaai.com/VehicleDetail/12345678~US")).isEmpty();
        assertThat(resolver.resolve("   ")).isEmpty();
    }

    @Test
    void copartLotNumberFromUrl() {
        assertThat(ApibaraVehicleResolver.copartLotNumber(COPART_URL)).contains("61747696");
        assertThat(ApibaraVehicleResolver.copartLotNumber("copart.com/lot/61747696")).contains("61747696");
        assertThat(ApibaraVehicleResolver.copartLotNumber("https://www.iaai.com/VehicleDetail/12345678~US")).isEmpty();
    }

    private static ApibaraVehicleDetailResponse fixture() throws Exception {
        try (InputStream in = ApibaraVehicleResolverTest.class.getResourceAsStream("/apibara/urltodetails-copart.json")) {
            return new ObjectMapper().readValue(in, ApibaraVehicleDetailResponse.class);
        }
    }
}
