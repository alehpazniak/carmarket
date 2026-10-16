package com.carmarket.importorder.controller;

import com.carmarket.importorder.StorageEnabledTestBase;
import com.carmarket.importorder.validation.FileTypeTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderFilesApiTest extends StorageEnabledTestBase {

    @Test
    void uploadsUnderTheOrderPrefixWithTheDetectedType() throws Exception {
        Created order = createOrder(dealerA());

        upload(order.id(), "DOCUMENT", "INVOICE_DEALER", file("../../Faktura VAT nr 1/2026.PDF", FileTypeTest.PDF))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].originalName").value("2026.PDF"))
            .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
            .andExpect(jsonPath("$[0].clientVisible").value(false));
        // Content-Type says PDF but the bytes are a PNG: stored as a PNG, extension fixed
        upload(order.id(), "PHOTO", "LOADING", new MockMultipartFile("files", "zdjęcie.pdf", "application/pdf", FileTypeTest.PNG))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].originalName").value("zdjęcie.png"))
            .andExpect(jsonPath("$[0].contentType").value("image/png"));

        ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, times(2)).putObject(puts.capture(), any(RequestBody.class));
        assertThat(puts.getAllValues().get(0).key())
            .matches("import-orders/" + order.id() + "/documents/[0-9a-f-]{36}\\.pdf");
        assertThat(puts.getAllValues().get(1).key())
            .matches("import-orders/" + order.id() + "/photos/[0-9a-f-]{36}\\.png");
        assertThat(puts.getAllValues().get(1).contentType()).isEqualTo("image/png");
        assertThat(puts.getAllValues()).allSatisfy(p -> assertThat(p.bucket()).isEqualTo("test-bucket"));
    }

    @Test
    void rejectsFilesByContentNotByName() throws Exception {
        Created order = createOrder(dealerA());
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        upload(order.id(), "DOCUMENT", "OTHER", new MockMultipartFile("files", "faktura.pdf", "application/pdf", html))
            .andExpect(status().isBadRequest());
        upload(order.id(), "PHOTO", "AUCTION", file("skan.pdf", FileTypeTest.PDF))
            .andExpect(status().isBadRequest());
        upload(order.id(), "DOCUMENT", "OTHER", file("pusty.pdf", new byte[0]))
            .andExpect(status().isBadRequest());
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void enforcesSizeCountAndCategory() throws Exception {
        Created order = createOrder(dealerA());

        byte[] bigPhoto = Arrays.copyOf(FileTypeTest.JPEG, 15 * 1024 * 1024 + 1);
        upload(order.id(), "PHOTO", "AUCTION", file("duze.jpg", bigPhoto)).andExpect(status().isPayloadTooLarge());
        byte[] okPhoto = Arrays.copyOf(FileTypeTest.JPEG, 15 * 1024 * 1024);
        upload(order.id(), "PHOTO", "AUCTION", file("ok.jpg", okPhoto)).andExpect(status().isCreated());

        byte[] bigDocument = Arrays.copyOf(FileTypeTest.PDF, 20 * 1024 * 1024 + 1);
        upload(order.id(), "DOCUMENT", "OTHER", file("duzy.pdf", bigDocument)).andExpect(status().isPayloadTooLarge());

        MockMultipartFile[] tooMany = new MockMultipartFile[31];
        for (int i = 0; i < tooMany.length; i++) tooMany[i] = file("p" + i + ".jpg", FileTypeTest.JPEG);
        upload(order.id(), "PHOTO", "AUCTION", tooMany).andExpect(status().isBadRequest());

        upload(order.id(), "PHOTO", "INVOICE_DEALER", file("a.jpg", FileTypeTest.JPEG)).andExpect(status().isBadRequest());
        upload(order.id(), "DOCUMENT", "AUCTION", file("a.pdf", FileTypeTest.PDF)).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from import_order_files", Integer.class)).isEqualTo(1);
    }

    @Test
    void thirtyPhotosInOneUpload() throws Exception {
        Created order = createOrder(dealerA());
        MockMultipartFile[] photos = new MockMultipartFile[30];
        for (int i = 0; i < photos.length; i++) photos[i] = file("p" + i + ".webp", FileTypeTest.WEBP);

        upload(order.id(), "PHOTO", "US_WAREHOUSE", photos)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$", hasSize(30)));
    }

    @Test
    void failedUploadRemovesWhatWasAlreadyStored() throws Exception {
        Created order = createOrder(dealerA());
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build())
            .thenThrow(S3Exception.builder().message("Access Denied").statusCode(403).build());

        upload(order.id(), "PHOTO", "AUCTION", file("a.jpg", FileTypeTest.JPEG), file("b.jpg", FileTypeTest.JPEG))
            .andExpect(status().isBadGateway());

        // The first photo made it to S3 before the second failed: it is deleted again, no rows are written
        ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, times(2)).putObject(puts.capture(), any(RequestBody.class));
        ArgumentCaptor<DeleteObjectRequest> deletes = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(deletes.capture());
        assertThat(deletes.getValue().key()).isEqualTo(puts.getAllValues().get(0).key());
        assertThat(jdbc.queryForObject("select count(*) from import_order_files", Integer.class)).isZero();
    }

    @Test
    void downloadUrlsArePresignedForFiveMinutes() throws Exception {
        Created order = createOrder(dealerA());
        String doc = id(upload(order.id(), "DOCUMENT", "INVOICE_DEALER", file("Faktura żółw.pdf", FileTypeTest.PDF)));
        String photo = id(upload(order.id(), "PHOTO", "ARRIVAL", file("port.jpg", FileTypeTest.JPEG)));

        mvc.perform(get("/import-orders/{id}/files/{fileId}/url", order.id(), doc).with(dealerA()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").exists())
            .andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(get("/import-orders/{id}/files/{fileId}/url", order.id(), photo).with(admin()))
            .andExpect(status().isOk());
        mvc.perform(get("/import-orders/{id}/files/{fileId}/url", order.id(), photo).with(dealerB()))
            .andExpect(status().isNotFound());

        ArgumentCaptor<GetObjectPresignRequest> presigns = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner, atLeastOnce()).presignGetObject(presigns.capture());
        List<GetObjectPresignRequest> all = presigns.getAllValues();
        assertThat(all.get(0).signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(all.get(0).getObjectRequest().responseContentDisposition())
            .isEqualTo("attachment; filename*=UTF-8''Faktura%20%C5%BC%C3%B3%C5%82w.pdf");
        assertThat(all.get(0).getObjectRequest().responseContentType()).isEqualTo("application/pdf");
        assertThat(all.get(1).getObjectRequest().responseContentDisposition()).isEqualTo("inline");
    }

    @Test
    void patchAndDeleteFiles() throws Exception {
        Created order = createOrder(dealerA());
        String doc = id(upload(order.id(), "DOCUMENT", "OTHER", file("a.pdf", FileTypeTest.PDF)));
        String key = jdbc.queryForObject("select s3_key from import_order_files where id = ?::uuid", String.class, doc);

        mvc.perform(patch("/import-orders/{id}/files/{fileId}", order.id(), doc).with(dealerA())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientVisible\":true,\"category\":\"CUSTOMS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientVisible").value(true))
            .andExpect(jsonPath("$.category").value("CUSTOMS"));
        mvc.perform(patch("/import-orders/{id}/files/{fileId}", order.id(), doc).with(dealerA())
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"LOADING\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/import-orders/{id}/files/{fileId}", order.id(), doc).with(dealerB()))
            .andExpect(status().isNotFound());

        mvc.perform(delete("/import-orders/{id}/files/{fileId}", order.id(), doc).with(dealerA()))
            .andExpect(status().isNoContent());

        ArgumentCaptor<DeleteObjectRequest> deletes = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(deletes.capture());
        assertThat(deletes.getValue().key()).isEqualTo(key);
        assertThat(jdbc.queryForObject("select count(*) from import_order_files", Integer.class)).isZero();
    }

    @Test
    void deletingAnOrderDeletesItsObjects() throws Exception {
        Created order = createOrder(dealerA());
        upload(order.id(), "DOCUMENT", "OTHER", file("a.pdf", FileTypeTest.PDF));
        upload(order.id(), "PHOTO", "OTHER", file("b.jpg", FileTypeTest.JPEG));
        List<String> keys = jdbc.queryForList("select s3_key from import_order_files", String.class);

        mvc.perform(delete("/import-orders/{id}", order.id()).with(dealerA())).andExpect(status().isNoContent());

        ArgumentCaptor<DeleteObjectsRequest> deletes = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3).deleteObjects(deletes.capture());
        assertThat(deletes.getValue().delete().objects()).extracting(ObjectIdentifier::key)
            .containsExactlyInAnyOrderElementsOf(keys);
        assertThat(jdbc.queryForObject("select count(*) from import_order_files", Integer.class)).isZero();
    }

    @Test
    void listThumbnailPrefersAnAuctionPhotoAndContractsAreADocumentCategory() throws Exception {
        Created order = createOrder(dealerA());
        upload(order.id(), "PHOTO", "ARRIVAL", file("port.png", FileTypeTest.PNG)).andExpect(status().isCreated());
        String auctionKey = body(upload(order.id(), "PHOTO", "AUCTION", file("aukcja.png", FileTypeTest.PNG))
            .andExpect(status().isCreated()).andReturn()).get(0).get("id").asText();
        upload(order.id(), "DOCUMENT", "CONTRACT", file("umowa.pdf", FileTypeTest.PDF))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].category").value("CONTRACT"));

        String s3Key = jdbc.queryForObject("select s3_key from import_order_files where id = ?::uuid", String.class, auctionKey);
        mvc.perform(get("/import-orders").with(dealerA()))
            .andExpect(jsonPath("$.content[0].thumbnailUrl").value(containsString(s3Key)));
        // Another dealer's list never carries this order's thumbnail
        mvc.perform(get("/import-orders").with(dealerB()))
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private ResultActions upload(UUID orderId, String kind, String category, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/import-orders/{id}/files", orderId);
        for (MockMultipartFile f : files) request.file(f);
        return mvc.perform(request.param("kind", kind).param("category", category).with(dealerA()));
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    private String id(ResultActions result) throws Exception {
        MvcResult r = result.andExpect(status().isCreated()).andReturn();
        return body(r).get(0).get("id").asText();
    }
}
