package com.carmarket.importorder.controller;

import com.carmarket.importorder.StorageEnabledTestBase;
import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.kafka.ImportOrderEventProducer;
import com.carmarket.importorder.kafka.ImportOrderEventProducer.ClientUpdateEvent;
import com.carmarket.importorder.repository.ImportOrderRepository;
import com.carmarket.importorder.validation.FileTypeTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PR 3: import-order.client-update is published after commit, only when the client should hear about it. */
class ClientNotificationEventsTest extends StorageEnabledTestBase {

    private static final String TOPIC = "import-order.client-update";

    @Autowired
    private ImportOrderEventProducer producer;

    @Autowired
    private ImportOrderRepository orders;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Created notifiedOrder(boolean notify, String email) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("source", "COPART");
        body.put("lotNumber", UUID.randomUUID().toString().substring(0, 8));
        body.put("make", "Audi");
        body.put("model", "Q5");
        body.put("year", 2019);
        body.put("clientName", "Jan Kowalski");
        body.put("clientEmail", email);
        body.put("notifyClient", notify);
        body.put("dealerContactName", "Auto Komis");
        Created order = createOrder(dealerA(), body);
        clearInvocations(kafkaTemplate);
        return order;
    }

    private ResultActions addEvent(UUID orderId, String status, Instant at, String clientNote) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("status", status, "occurredAt", at.toString()));
        if (clientNote != null) body.put("clientNote", clientNote);
        return mvc.perform(post("/import-orders/{id}/events", orderId).with(dealerA())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
    }

    private ClientUpdateEvent sentEvent() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC), anyString(), payload.capture());
        return (ClientUpdateEvent) payload.getValue();
    }

    @Test
    void statusChangeSendsTheLinkButNeverThePassword() throws Exception {
        Created order = notifiedOrder(true, "klient@example.com");

        addEvent(order.id(), "WON", Instant.now(), null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(order.id().toString()), payload.capture());
        ClientUpdateEvent event = (ClientUpdateEvent) payload.getValue();
        assertThat(event.orderId()).isEqualTo(order.id().toString());
        assertThat(event.clientEmail()).isEqualTo("klient@example.com");
        assertThat(event.clientName()).isEqualTo("Jan Kowalski");
        assertThat(event.dealerName()).isEqualTo("Auto Komis");
        assertThat(event.carTitle()).isEqualTo("Audi Q5 2019");
        assertThat(event.link()).isEqualTo(order.clientLink());
        assertThat(event.reason()).isEqualTo(ImportOrderEventProducer.Reason.STATUS);
        String serialized = json.writeValueAsString(event);
        assertThat(serialized).doesNotContain(order.password()).doesNotContain(order.password().replace("-", ""))
            .doesNotContain("$2a$");
    }

    @Test
    void aClientNoteAlwaysNotifiesABackdatedStepWithoutOneDoesNot() throws Exception {
        Created order = notifiedOrder(true, "klient@example.com");
        addEvent(order.id(), "LOADED", Instant.now(), null);
        clearInvocations(kafkaTemplate);

        // Backdated, no note: history only, the current status stays LOADED
        addEvent(order.id(), "AUCTION_PAID", Instant.now().minus(5, ChronoUnit.DAYS), null);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

        // Same situation with a note for the client
        addEvent(order.id(), "WON", Instant.now().minus(9, ChronoUnit.DAYS), "Mamy dokumenty z aukcji");
        assertThat(sentEvent().reason()).isEqualTo(ImportOrderEventProducer.Reason.STATUS);
    }

    @Test
    void deletingTheCurrentStepIsAStatusChange() throws Exception {
        Created order = notifiedOrder(true, "klient@example.com");
        addEvent(order.id(), "WON", Instant.now().minus(2, ChronoUnit.DAYS), null);
        MvcResult loaded = addEvent(order.id(), "LOADED", Instant.now(), null).andReturn();
        String loadedId = body(loaded).get("events").get(0).get("id").asText();
        clearInvocations(kafkaTemplate);

        mvc.perform(delete("/import-orders/{id}/events/{eventId}", order.id(), loadedId).with(dealerA()))
            .andExpect(status().isOk());

        verify(kafkaTemplate, times(1)).send(eq(TOPIC), anyString(), any());
    }

    @Test
    void nothingIsSentWithoutConsentEmailOrOpenAccess() throws Exception {
        Created off = notifiedOrder(false, "klient@example.com");
        addEvent(off.id(), "WON", Instant.now(), "Notatka");

        Created noEmail = notifiedOrder(true, null);
        addEvent(noEmail.id(), "WON", Instant.now(), "Notatka");

        Created disabled = notifiedOrder(true, "klient@example.com");
        mvc.perform(patch("/import-orders/{id}/access", disabled.id()).with(dealerA())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        addEvent(disabled.id(), "WON", Instant.now(), "Notatka");

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void visibleDocumentsNotifyHiddenOnesAndPhotosDoNot() throws Exception {
        Created order = notifiedOrder(true, "klient@example.com");

        String hidden = upload(order.id(), "DOCUMENT", "OTHER", "koszty.pdf", FileTypeTest.PDF, false);
        upload(order.id(), "PHOTO", "LOADING", "a.jpg", FileTypeTest.JPEG, true);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

        upload(order.id(), "DOCUMENT", "INVOICE_DEALER", "faktura.pdf", FileTypeTest.PDF, true);
        assertThat(sentEvent().reason()).isEqualTo(ImportOrderEventProducer.Reason.DOCUMENT);
        clearInvocations(kafkaTemplate);

        // Sharing a hidden document later counts as new for the client
        mvc.perform(patch("/import-orders/{id}/files/{fileId}", order.id(), hidden).with(dealerA())
            .contentType(MediaType.APPLICATION_JSON).content("{\"clientVisible\":true}")).andExpect(status().isOk());
        assertThat(sentEvent().reason()).isEqualTo(ImportOrderEventProducer.Reason.DOCUMENT);
    }

    @Test
    void sentOnlyAfterCommit() throws Exception {
        Created order = notifiedOrder(true, "klient@example.com");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            ImportOrder o = orders.findById(order.id()).orElseThrow();
            assertThat(producer.publishClientUpdate(o, ImportOrderEventProducer.Reason.STATUS)).isTrue();
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any()); // not yet
            status.setRollbackOnly();
        });
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any()); // rolled back: never

        tx.executeWithoutResult(status ->
            producer.publishClientUpdate(orders.findById(order.id()).orElseThrow(), ImportOrderEventProducer.Reason.STATUS));
        verify(kafkaTemplate, times(1)).send(eq(TOPIC), anyString(), any());
    }

    private String upload(UUID orderId, String kind, String category, String name, byte[] content, boolean visible) throws Exception {
        MvcResult result = mvc.perform(multipart("/import-orders/{id}/files", orderId)
                .file(new MockMultipartFile("files", name, "application/octet-stream", content))
                .param("kind", kind).param("category", category).param("clientVisible", String.valueOf(visible))
                .with(dealerA()))
            .andExpect(status().isCreated())
            .andReturn();
        return body(result).get(0).get("id").asText();
    }
}
