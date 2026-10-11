package com.carmarket.importorder.controller;

import com.carmarket.importorder.IntegrationTestBase;
import com.carmarket.importorder.validation.FileTypeTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default config: the IMPORT_ORDER_* storage variables are empty. */
class StorageDisabledApiTest extends IntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    @Test
    void noS3BeansAreCreated() {
        assertThat(context.getBeanNamesForType(S3Client.class)).isEmpty();
        assertThat(context.getBeanNamesForType(S3Presigner.class)).isEmpty();
    }

    @Test
    void fileEndpointsAnswer503AndEverythingElseWorks() throws Exception {
        Created order = createOrder(dealerA());

        mvc.perform(multipart("/import-orders/{id}/files", order.id())
                .file(new MockMultipartFile("files", "a.pdf", "application/pdf", FileTypeTest.PDF))
                .param("kind", "DOCUMENT").param("category", "OTHER")
                .with(dealerA()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Storage not configured"));
        mvc.perform(get("/import-orders/{id}/files/{fileId}/url", order.id(), UUID.randomUUID()).with(dealerA()))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/import-orders/{id}/files/{fileId}", order.id(), UUID.randomUUID()).with(dealerA()))
            .andExpect(status().isServiceUnavailable());

        mvc.perform(get("/import-orders/{id}", order.id()).with(dealerA()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storageEnabled").value(false));
        mvc.perform(patch("/import-orders/{id}", order.id()).with(dealerA())
                .contentType(MediaType.APPLICATION_JSON).content("{\"vesselName\":\"MSC Anna\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/import-orders/{id}/events", order.id()).with(dealerA())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"WON\",\"occurredAt\":\"2026-10-01T10:00:00Z\"}"))
            .andExpect(status().isOk());

        String token = body(mvc.perform(post("/import-orders/public/{code}/access", order.code())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("password", order.password()))))
            .andExpect(status().isOk()).andReturn()).get("token").asText();
        mvc.perform(get("/import-orders/public/{code}", order.code()).header("X-Order-Token", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shipping.vesselName").value("MSC Anna"))
            .andExpect(jsonPath("$.documents").isEmpty());

        // Without files an order can still be deleted
        mvc.perform(delete("/import-orders/{id}", order.id()).with(dealerA())).andExpect(status().isNoContent());
    }
}
