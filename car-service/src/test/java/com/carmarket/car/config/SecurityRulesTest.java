package com.carmarket.car.config;

import com.carmarket.car.audit.AdminAuditPublisher;
import com.carmarket.car.controller.CarListingController;
import com.carmarket.car.controller.ReindexController;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.service.CarListingService;
import com.carmarket.car.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real {@link SecurityConfig} + GatewaySignatureFilter (unlike {@link SecurityConfigTest},
 * which permits everything) with requests signed the way the gateway signs them.
 */
@WebMvcTest({ReindexController.class, CarListingController.class, com.carmarket.car.controller.AdminListingController.class,
    com.carmarket.car.controller.DealerMediaController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "gateway.internal-secret=" + SecurityRulesTest.SECRET)
@DisplayName("car-service security rules")
class SecurityRulesTest {

    static final String SECRET = "test-gateway-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CarListingRepository carListingRepository;

    @MockBean
    private CarEventProducer eventProducer;

    @MockBean
    private CarListingService carListingService;

    @MockBean
    private S3Service s3Service;

    @MockBean
    private AdminAuditPublisher auditPublisher;

    @MockBean
    private com.carmarket.car.service.ListingReportService listingReportService;

    @MockBean
    private com.carmarket.car.service.AdminListingService adminListingService;

    @BeforeEach
    void setUp() {
        when(carListingRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
    }

    @Test
    @DisplayName("reindex: a regular logged-in user gets 403")
    void reindex_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(signed(post("/cars/admin/reindex"), "USER"))
            .andExpect(status().isForbidden());
        verify(carListingRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("reindex: anonymous request (e.g. straight to :8083) gets 403")
    void reindex_anonymous_forbidden() throws Exception {
        mockMvc.perform(post("/cars/admin/reindex"))
            .andExpect(status().isForbidden());
        verify(carListingRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("reindex: forged roles header without a valid signature gets 403")
    void reindex_unsignedAdminHeader_forbidden() throws Exception {
        mockMvc.perform(post("/cars/admin/reindex")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("reindex: GET is not opened up by the public GET /cars/** rule")
    void reindex_get_notPublic() throws Exception {
        mockMvc.perform(get("/cars/admin/reindex"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("reindex: an admin is allowed and the action is audited")
    void reindex_admin_ok() throws Exception {
        mockMvc.perform(signed(post("/cars/admin/reindex"), "ADMIN").header("X-User-Email", "boss@example.com"))
            .andExpect(status().isOk());
        verify(carListingRepository).findAll(any(Pageable.class));
        verify(auditPublisher).publish(argThat(a -> "boss@example.com".equals(a.email())), eq("SEARCH_REINDEXED"),
            eq("SEARCH_INDEX"), eq("car_listings"), isNull(), eq(java.util.Map.of("published", 0L)));
    }

    private static MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String... roles) throws Exception {
        String userId = UUID.randomUUID().toString();
        long ts = System.currentTimeMillis();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal((userId + ":" + ts).getBytes(StandardCharsets.UTF_8)));
        return request
            .header("X-User-Id", userId)
            .header("X-User-Roles", String.join(",", roles))
            .header("X-Gateway-Signature", ts + ":" + sig);
    }

    @Test
    @DisplayName("admin listing endpoints: USER 403, ADMIN 200")
    void adminListingEndpoints() throws Exception {
        String id = UUID.randomUUID().toString();
        String reason = "{\"reason\":\"fake listing\"}";
        java.util.List<java.util.function.Supplier<MockHttpServletRequestBuilder>> requests = java.util.List.of(
            () -> get("/cars/admin/listings"), () -> get("/cars/admin/listings/" + id), () -> get("/cars/admin/reports"),
            () -> get("/cars/admin/stats"),
            () -> post("/cars/admin/listings/" + id + "/block").contentType("application/json").content(reason),
            () -> post("/cars/admin/listings/" + id + "/unblock").contentType("application/json").content(reason),
            () -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/cars/admin/listings/" + id + "/images")
                .param("url", "https://s3/a.jpg").contentType("application/json").content(reason),
            () -> post("/cars/admin/reports/listing/" + id + "/dismiss").contentType("application/json").content("{\"note\":\"looks fine\"}"));
        for (var request : requests) {
            mockMvc.perform(signed(request.get(), "USER")).andExpect(status().isForbidden());
            mockMvc.perform(request.get()).andExpect(status().isForbidden());
            mockMvc.perform(signed(request.get(), "ADMIN")).andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    @DisplayName("/cars/my/{id}: unsigned request gets 403 despite the public GET /cars/** rule")
    void myListing_unsigned_forbidden() throws Exception {
        mockMvc.perform(get("/cars/my/" + UUID.randomUUID()).header("X-User-Id", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/cars/my").header("X-User-Id", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        verify(carListingService, never()).getMine(any(), any());
    }

    @Test
    @DisplayName("reporting needs a signed caller")
    void report_unsigned_forbidden() throws Exception {
        mockMvc.perform(post("/cars/" + UUID.randomUUID() + "/reports").contentType("application/json")
                .content("{\"reason\":\"SCAM\"}").header("X-User-Id", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        mockMvc.perform(signed(post("/cars/" + UUID.randomUUID() + "/reports").contentType("application/json")
                .content("{\"reason\":\"SCAM\"}"), "USER"))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("summaries: unsigned request (gateway bypassed) gets 403, unlike the public GET /cars/**")
    void summaries_unsigned_forbidden() throws Exception {
        mockMvc.perform(get("/cars/summaries").param("ids", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        verify(carListingService, never()).getSummaries(any());
    }

    @Test
    @DisplayName("summaries: a signed request is allowed")
    void summaries_signed_ok() throws Exception {
        when(carListingService.getSummaries(any())).thenReturn(List.of());

        mockMvc.perform(signed(get("/cars/summaries").param("ids", UUID.randomUUID().toString()), "USER"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /cars/{id} stays public")
    void listing_unsigned_public() throws Exception {
        mockMvc.perform(get("/cars/{id}", UUID.randomUUID()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /cars/seller/{id} (dealer page offers) is public")
    void sellerListings_unsigned_public() throws Exception {
        when(carListingService.getSellerListings(any(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/cars/seller/{id}", UUID.randomUUID()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("dealer-media: DEALER/ADMIN only, never unsigned")
    void dealerMedia_dealersOnly() throws Exception {
        org.springframework.mock.web.MockMultipartFile file =
            new org.springframework.mock.web.MockMultipartFile("file", "logo.png", "image/png", new byte[]{1});
        when(s3Service.uploadDealerImage(any(), any())).thenReturn("https://b.s3.eu-central-1.amazonaws.com/dealers/x/y.png");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/cars/dealer-media").file(file)
                .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Roles", "DEALER"))
            .andExpect(status().isForbidden());
        mockMvc.perform(signed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/cars/dealer-media").file(file), "USER"))
            .andExpect(status().isForbidden());
        verify(s3Service, never()).uploadDealerImage(any(), any());

        mockMvc.perform(signed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/cars/dealer-media").file(file), "DEALER"))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("dealer-media DELETE: someone else's image is a 404")
    void dealerMedia_delete_ownOnly() throws Exception {
        when(s3Service.deleteDealerImage(any(), eq("https://mine"))).thenReturn(true);
        mockMvc.perform(signed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/cars/dealer-media")
                .param("url", "https://mine"), "DEALER"))
            .andExpect(status().isNoContent());
        mockMvc.perform(signed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/cars/dealer-media")
                .param("url", "https://not-mine"), "DEALER"))
            .andExpect(status().isNotFound());
    }
}
