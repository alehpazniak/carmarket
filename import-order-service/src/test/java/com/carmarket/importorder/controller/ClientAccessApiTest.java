package com.carmarket.importorder.controller;

import com.carmarket.importorder.StorageEnabledTestBase;
import com.carmarket.importorder.validation.FileTypeTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientAccessApiTest extends StorageEnabledTestBase {

    @Test
    void correctPasswordGivesATokenForTheOrderView() throws Exception {
        Created order = createOrder(dealerA());

        String token = login(order.code(), order.password());
        mvc.perform(get("/import-orders/public/{code}", order.code()).header("X-Order-Token", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.make").value("Audi"));

        // Case, spaces and dashes don't matter when typing it on a phone
        login(order.code(), " " + order.password().replace("-", " ").toUpperCase() + " ");
    }

    @Test
    void wrongPasswordAndUnknownCodeLookTheSame() throws Exception {
        Created order = createOrder(dealerA());

        MvcResult wrongPassword = access(order.code(), "aaaa-bbbb-cccc").andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknownCode = access("AAAAAAAAAAAAAAAAAAAAAA", order.password()).andExpect(status().isUnauthorized()).andReturn();
        MvcResult garbageCode = access("not a code!", order.password()).andExpect(status().isUnauthorized()).andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
            .isEqualTo(unknownCode.getResponse().getContentAsString())
            .isEqualTo(garbageCode.getResponse().getContentAsString());
    }

    @Test
    void tenWrongPasswordsLockTheOrder() throws Exception {
        Created order = createOrder(dealerA());

        for (int i = 1; i <= 9; i++) {
            access(order.code(), "wrong-" + i).andExpect(status().isUnauthorized());
        }
        access(order.code(), "wrong-10")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "900"))
            .andExpect(jsonPath("$.retryAfterSeconds").value(900));

        // Locked: even the right password is refused for now
        access(order.code(), order.password())
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));

        // Once the lock has expired, the right password works and the counter starts again
        jdbc.update("update import_orders set locked_until = now() - interval '1 second' where id = ?", order.id());
        login(order.code(), order.password());
        assertThat(jdbc.queryForObject("select failed_attempts from import_orders where id = ?", Integer.class, order.id()))
            .isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from import_order_access_log where order_id = ? and event = 'LOGIN_FAILED'",
            Integer.class, order.id())).isEqualTo(10);
    }

    @Test
    void successResetsTheFailureCounter() throws Exception {
        Created order = createOrder(dealerA());
        for (int i = 0; i < 5; i++) access(order.code(), "wrong").andExpect(status().isUnauthorized());

        login(order.code(), order.password());

        for (int i = 0; i < 9; i++) access(order.code(), "wrong").andExpect(status().isUnauthorized());
    }

    @Test
    void resetInvalidatesOldTokensAndPassword() throws Exception {
        Created order = createOrder(dealerA());
        String oldToken = login(order.code(), order.password());

        MvcResult reset = mvc.perform(post("/import-orders/{id}/access/reset", order.id()).with(dealerA()))
            .andExpect(status().isOk())
            .andReturn();
        String newPassword = body(reset).get("password").asText();
        assertThat(body(reset).get("clientLink").asText()).isEqualTo(order.clientLink());

        view(order.code(), oldToken).andExpect(status().isUnauthorized());
        access(order.code(), order.password()).andExpect(status().isUnauthorized());
        view(order.code(), login(order.code(), newPassword)).andExpect(status().isOk());
    }

    @Test
    void resetWithRotateLinkKillsTheOldLink() throws Exception {
        Created order = createOrder(dealerA());

        MvcResult reset = mvc.perform(post("/import-orders/{id}/access/reset", order.id()).with(dealerA())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rotateLink\":true}"))
            .andExpect(status().isOk())
            .andReturn();
        String link = body(reset).get("clientLink").asText();
        String newCode = link.substring(link.lastIndexOf('/') + 1);
        String newPassword = body(reset).get("password").asText();

        assertThat(newCode).isNotEqualTo(order.code());
        access(order.code(), newPassword).andExpect(status().isUnauthorized());
        login(newCode, newPassword);
    }

    @Test
    void disabledOrArchivedOrdersAreClosed() throws Exception {
        Created order = createOrder(dealerA());
        String token = login(order.code(), order.password());

        mvc.perform(patch("/import-orders/{id}/access", order.id()).with(dealerA())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(jsonPath("$.access.enabled").value(false));
        view(order.code(), token).andExpect(status().isUnauthorized());
        access(order.code(), order.password()).andExpect(status().isUnauthorized());

        mvc.perform(patch("/import-orders/{id}/access", order.id()).with(dealerA())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"));
        view(order.code(), token).andExpect(status().isOk());

        mvc.perform(post("/import-orders/{id}/archive", order.id()).with(dealerA())).andExpect(status().isOk());
        view(order.code(), token).andExpect(status().isUnauthorized());
        access(order.code(), order.password()).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenOnlyOpensItsOwnOrder() throws Exception {
        Created first = createOrder(dealerA());
        Created second = createOrder(dealerA());
        String token = login(first.code(), first.password());

        view(second.code(), token).andExpect(status().isUnauthorized());
        view(first.code(), "garbage").andExpect(status().isUnauthorized());
        mvc.perform(get("/import-orders/public/{code}", first.code())).andExpect(status().isUnauthorized());
    }

    @Test
    void clientViewShowsOnlyWhatIsMeantForTheClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "IAAI");
        body.put("lotNumber", "3333");
        body.put("make", "Toyota");
        body.put("purchasePriceUsd", 12500);
        body.put("clientName", "Jan Kowalski");
        body.put("clientEmail", "jan.secret@example.com");
        body.put("clientPhone", "+48 600 111 222");
        body.put("dealerContactName", "Auto Komis");
        body.put("dealerContactPhone", "+48 500 000 000");
        body.put("internalNotes", "MARGIN-NOTE-SECRET");
        body.put("auctionSnapshot", Map.of("seller", "INSURANCE-SECRET"));
        Created order = createOrder(dealerA(), body);
        mvc.perform(post("/import-orders/{id}/events", order.id()).with(dealerA())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "WON", "occurredAt", Instant.now().toString(),
                    "clientNote", "Auto wygrane!", "internalNote", "EVENT-NOTE-SECRET"))))
            .andExpect(status().isOk());
        upload(order.id(), "faktura.pdf", true);
        upload(order.id(), "HIDDEN-FILE-SECRET.pdf", false);

        String token = login(order.code(), order.password());
        MvcResult result = view(order.code(), token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.make").value("Toyota"))
            .andExpect(jsonPath("$.status").value("WON"))
            .andExpect(jsonPath("$.events[0].note").value("Auto wygrane!"))
            .andExpect(jsonPath("$.events[0].internalNote").doesNotExist())
            .andExpect(jsonPath("$.documents", hasSize(1)))
            .andExpect(jsonPath("$.documents[0].originalName").value("faktura.pdf"))
            .andExpect(jsonPath("$.documents[0].url").exists())
            .andExpect(jsonPath("$.dealer.name").value("Auto Komis"))
            .andExpect(jsonPath("$.internalNotes").doesNotExist())
            .andExpect(jsonPath("$.dealerId").doesNotExist())
            .andExpect(jsonPath("$.clientEmail").doesNotExist())
            .andExpect(jsonPath("$.clientPhone").doesNotExist())
            .andExpect(jsonPath("$.clientName").doesNotExist())
            .andExpect(jsonPath("$.purchasePriceUsd").doesNotExist())
            .andExpect(jsonPath("$.auctionSnapshot").doesNotExist())
            .andExpect(jsonPath("$.access").doesNotExist())
            .andExpect(jsonPath("$.accessVersion").doesNotExist())
            .andExpect(jsonPath("$.publicCode").doesNotExist())
            .andExpect(jsonPath("$.id").doesNotExist())
            .andReturn();
        String raw = result.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("MARGIN-NOTE-SECRET", "EVENT-NOTE-SECRET", "HIDDEN-FILE-SECRET",
            "jan.secret@example.com", "600 111 222", "INSURANCE-SECRET", "12500", DEALER_A.toString(), order.code());
        // (The order id does appear inside presigned URLs: object keys are import-orders/{orderId}/...)

        // The dealer's preview is exactly the client view (except the freshly signed URLs)
        JsonNode preview = body(mvc.perform(get("/import-orders/{id}/client-preview", order.id()).with(dealerA()))
            .andExpect(status().isOk()).andReturn());
        JsonNode client = json.readTree(raw);
        assertThat(withoutUrls(preview)).isEqualTo(withoutUrls(client));
    }

    @Test
    void hiddenFilesCantBeFetchedThroughThePublicEndpoint() throws Exception {
        Created order = createOrder(dealerA());
        String visible = upload(order.id(), "faktura.pdf", true);
        String hidden = upload(order.id(), "wewnetrzny.pdf", false);
        String token = login(order.code(), order.password());

        mvc.perform(get("/import-orders/public/{code}/files/{fileId}/url", order.code(), visible)
                .header("X-Order-Token", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").exists());
        mvc.perform(get("/import-orders/public/{code}/files/{fileId}/url", order.code(), hidden)
                .header("X-Order-Token", token))
            .andExpect(status().isNotFound());
        mvc.perform(get("/import-orders/public/{code}/files/{fileId}/url", order.code(), visible))
            .andExpect(status().isUnauthorized());

        // Another order's file, with a valid token for this one
        Created other = createOrder(dealerA());
        String foreign = upload(other.id(), "obcy.pdf", true);
        mvc.perform(get("/import-orders/public/{code}/files/{fileId}/url", order.code(), foreign)
                .header("X-Order-Token", token))
            .andExpect(status().isNotFound());
    }

    @Test
    void viewIsLoggedOncePerTokenPerHour() throws Exception {
        Created order = createOrder(dealerA());
        String token = login(order.code(), order.password());

        view(order.code(), token).andExpect(status().isOk());
        view(order.code(), token).andExpect(status().isOk());
        mvc.perform(get("/import-orders/{id}/client-preview", order.id()).with(dealerA())).andExpect(status().isOk());

        mvc.perform(get("/import-orders/{id}/access-log", order.id()).with(dealerA()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].event").value("VIEW"))
            .andExpect(jsonPath("$[1].event").value("LOGIN_OK"))
            .andExpect(jsonPath("$[0].ipPrefix").value("127.0.0.0/24"));

        // A second login (new token) is a new VIEW
        view(order.code(), login(order.code(), order.password())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from import_order_access_log where event = 'VIEW'", Integer.class))
            .isEqualTo(2);
    }

    private ResultActions access(String code, String password) throws Exception {
        return mvc.perform(post("/import-orders/public/{code}/access", code)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("password", password))));
    }

    private String login(String code, String password) throws Exception {
        MvcResult result = access(code, password)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andReturn();
        return body(result).get("token").asText();
    }

    private ResultActions view(String code, String token) throws Exception {
        return mvc.perform(get("/import-orders/public/{code}", code).header("X-Order-Token", token));
    }

    private String upload(UUID orderId, String name, boolean visible) throws Exception {
        MvcResult result = mvc.perform(multipart("/import-orders/{id}/files", orderId)
                .file(new MockMultipartFile("files", name, "application/pdf", FileTypeTest.PDF))
                .param("kind", "DOCUMENT")
                .param("category", "INVOICE_DEALER")
                .param("clientVisible", String.valueOf(visible))
                .with(dealerA()))
            .andExpect(status().isCreated())
            .andReturn();
        return body(result).get(0).get("id").asText();
    }

    private static JsonNode withoutUrls(JsonNode view) {
        JsonNode copy = view.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy).remove("urlsExpireAt");
        for (String list : new String[]{"documents", "photos"}) {
            copy.get(list).forEach(f -> ((com.fasterxml.jackson.databind.node.ObjectNode) f).remove("url"));
        }
        return copy;
    }
}
