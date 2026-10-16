package com.carmarket.importorder.controller;

import com.carmarket.importorder.StorageEnabledTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class DealerOrderApiTest extends StorageEnabledTestBase {

    @Test
    void createReturnsThePasswordOnceAndStoresOnlyItsHash(CapturedOutput output) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "COPART");
        body.put("lotNumber", "41234567");
        body.put("vin", "1m8gdm9a xkp042788");
        body.put("make", "Audi");
        body.put("model", "Q5");
        body.put("year", 2019);
        body.put("saleDate", "2026-10-01T15:00:00Z");
        body.put("titleType", "Clean title");
        body.put("titleExportOk", true);
        body.put("titleRegistrationOk", false);
        body.put("clientName", "Jan Kowalski");
        body.put("auctionSnapshot", Map.of("platform", "copart", "media", Map.of("thumbs", List.of("https://x/1.jpg"))));

        Created order = createOrder(dealerA(), body);

        assertThat(order.password()).matches("[a-z2-9]{4}-[a-z2-9]{4}-[a-z2-9]{4}");
        assertThat(order.clientLink()).isEqualTo("http://localhost:5173/zlecenie/" + order.code());
        assertThat(order.code()).matches("[A-Za-z0-9_-]{22}");
        JsonNode created = order.body().get("order");
        assertThat(created.get("vin").asText()).isEqualTo("1M8GDM9AXKP042788");
        assertThat(created.get("saleDate").asText()).isEqualTo("2026-10-01");
        assertThat(created.get("auctionSnapshot").get("platform").asText()).isEqualTo("copart");

        // Never again: not in the order detail…
        mvc.perform(get("/import-orders/{id}", order.id()).with(dealerA()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.access.clientLink").value(order.clientLink()));

        // …only a BCrypt hash (cost 12) in the database, and the plain text nowhere in it…
        String hash = jdbc.queryForObject("select password_hash from import_orders where id = ?", String.class, order.id());
        assertThat(hash).startsWith("$2a$12$");
        String normalized = order.password().replace("-", "");
        for (String table : List.of("import_orders", "import_order_events", "import_order_files", "import_order_access_log")) {
            String dump = jdbc.queryForObject("select coalesce(string_agg(row_to_json(t)::text, ''), '') from " + table + " t",
                String.class);
            assertThat(dump).doesNotContain(order.password()).doesNotContain(normalized);
        }
        // …and not in the logs
        assertThat(output.getAll()).doesNotContain(order.password()).doesNotContain(normalized);
    }

    @Test
    void ordersAreScopedToTheirDealer() throws Exception {
        Created order = createOrder(dealerA());
        String patchBody = "{\"clientName\":\"X\"}";

        // Another dealer: 404, exactly like a missing order
        mvc.perform(get("/import-orders/{id}", order.id()).with(dealerB())).andExpect(status().isNotFound());
        mvc.perform(patch("/import-orders/{id}", order.id()).with(dealerB())
            .contentType(MediaType.APPLICATION_JSON).content(patchBody)).andExpect(status().isNotFound());
        mvc.perform(delete("/import-orders/{id}", order.id()).with(dealerB())).andExpect(status().isNotFound());
        mvc.perform(get("/import-orders/{id}/client-preview", order.id()).with(dealerB())).andExpect(status().isNotFound());
        mvc.perform(post("/import-orders/{id}/access/reset", order.id()).with(dealerB())).andExpect(status().isNotFound());
        mvc.perform(get("/import-orders").with(dealerB()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));

        // Admin reads everything, but changes only their own orders
        mvc.perform(get("/import-orders/{id}", order.id()).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/import-orders").with(admin())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(patch("/import-orders/{id}", order.id()).with(admin())
            .contentType(MediaType.APPLICATION_JSON).content(patchBody)).andExpect(status().isNotFound());

        // A plain user, or a call that bypasses the gateway, is not let in at all
        mvc.perform(get("/import-orders/{id}", order.id()).with(plainUser())).andExpect(status().isForbidden());
        mvc.perform(get("/import-orders").with(plainUser())).andExpect(status().isForbidden());
        mvc.perform(get("/import-orders/{id}", order.id())
            .header("X-User-Id", DEALER_A.toString()).header("X-User-Roles", "DEALER"))
            .andExpect(status().isForbidden());

        mvc.perform(get("/import-orders/{id}", order.id()).with(dealerA())).andExpect(status().isOk());
    }

    @Test
    void currentStatusFollowsTheLatestOccurredAt() throws Exception {
        Created order = createOrder(dealerA());
        Instant now = Instant.now();

        addEvent(order.id(), "WON", now.minus(10, ChronoUnit.DAYS)).andExpect(jsonPath("$.status").value("WON"));
        String loaded = eventId(addEvent(order.id(), "LOADED", now.minus(1, ChronoUnit.DAYS))
            .andExpect(jsonPath("$.status").value("LOADED")), "LOADED");

        // Backdated: goes into the history but doesn't become the current status
        addEvent(order.id(), "AUCTION_PAID", now.minus(8, ChronoUnit.DAYS))
            .andExpect(jsonPath("$.status").value("LOADED"))
            .andExpect(jsonPath("$.events", hasSize(3)))
            .andExpect(jsonPath("$.events[0].status").value("LOADED"))
            .andExpect(jsonPath("$.events[1].status").value("AUCTION_PAID"));
        assertThat(jdbc.queryForObject("select status from import_orders where id = ?", String.class, order.id()))
            .isEqualTo("LOADED");

        // Deleting the latest event falls back to the next one
        mvc.perform(delete("/import-orders/{id}/events/{eventId}", order.id(), loaded).with(dealerA()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AUCTION_PAID"));

        // Same date: the event added last wins
        Instant sameDay = now.minus(2, ChronoUnit.HOURS);
        addEvent(order.id(), "ON_HOLD", sameDay);
        addEvent(order.id(), "PICKED_UP", sameDay).andExpect(jsonPath("$.status").value("PICKED_UP"));

        // Corrections are allowed in any order, e.g. back to an earlier step
        addEvent(order.id(), "WON", now.minus(1, ChronoUnit.HOURS)).andExpect(jsonPath("$.status").value("WON"));

        addEvent(order.id(), "WON", now.plus(3, ChronoUnit.DAYS)).andExpect(status().isBadRequest());
    }

    @Test
    void emptyTimelineMeansNoStatus() throws Exception {
        Created order = createOrder(dealerA());
        String won = eventId(addEvent(order.id(), "WON", Instant.now()), "WON");

        mvc.perform(delete("/import-orders/{id}/events/{eventId}", order.id(), won).with(dealerA()))
            .andExpect(jsonPath("$.status").doesNotExist());
        assertThat(jdbc.queryForObject("select status from import_orders where id = ?", String.class, order.id())).isNull();
    }

    @Test
    void patchValidatesAndNormalizes() throws Exception {
        Created order = createOrder(dealerA());

        patchOrder(order.id(), "{\"containerNumber\":\"csqu 305438-3\",\"eta\":\"2026-11-20\",\"notifyClient\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.containerNumber").value("CSQU3054383"))
            .andExpect(jsonPath("$.eta").value("2026-11-20"))
            .andExpect(jsonPath("$.notifyClient").value(true))
            .andExpect(jsonPath("$.make").value("Audi")); // absent fields stay as they were

        patchOrder(order.id(), "{\"containerNumber\":\"CSQU3054384\"}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"vin\":\"1M8GDM9AIKP042788\"}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"clientEmail\":\"nope\"}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"status\":\"HANDED_OVER\"}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"dealerId\":\"" + DEALER_B + "\"}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"auctionSnapshot\":{}}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"purchasePriceUsd\":-1}").andExpect(status().isBadRequest());

        // Wrong check digit: saved, with a warning
        patchOrder(order.id(), "{\"vin\":\"1M8GDM9A1KP042788\",\"containerNumber\":null}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vin").value("1M8GDM9A1KP042788"))
            .andExpect(jsonPath("$.containerNumber").doesNotExist())
            .andExpect(jsonPath("$.warnings[0]", containsString("check digit")));
    }

    @Test
    void oneOrderPerLotAndDealer() throws Exception {
        Map<String, Object> lot = Map.of("source", "COPART", "lotNumber", "55555555");
        createOrder(dealerA(), lot);

        mvc.perform(post("/import-orders").with(dealerA()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(lot)))
            .andExpect(status().isConflict());
        // Another dealer may have the same lot, and so may another auction house
        createOrder(dealerB(), lot);
        createOrder(dealerA(), Map.of("source", "IAAI", "lotNumber", "55555555"));
        mvc.perform(post("/import-orders").with(dealerA()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotNumber\":\"1\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void auctionLinkIsStoredValidatedAndListedWithTheSnapshotPhoto() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "COPART");
        body.put("lotNumber", "61747696");
        body.put("make", "Mazda");
        body.put("auctionUrl", "https://www.copart.com/lot/61747696/salvage-2021-mazda-cx-5-grand-touring-il-chicago-south");
        body.put("auctionSnapshot", Map.of("media", Map.of("items", List.of(
            Map.of("type", "video", "url", "https://img.example.com/walkaround.mp4"),
            Map.of("type", "image", "thumb", "https://img.example.com/1-thumb.jpg", "large", "https://img.example.com/1.jpg")))));
        Created order = createOrder(dealerA(), body);

        assertThat(order.body().get("order").get("auctionUrl").asText()).startsWith("https://www.copart.com/lot/61747696");
        mvc.perform(get("/import-orders").with(dealerA()))
            .andExpect(jsonPath("$.content[0].auctionUrl").value(containsString("copart.com/lot/61747696")))
            .andExpect(jsonPath("$.content[0].auctionPhotoUrl").value("https://img.example.com/1-thumb.jpg"))
            .andExpect(jsonPath("$.content[0].thumbnailUrl").doesNotExist());

        patchOrder(order.id(), "{\"auctionUrl\":\"javascript:alert(1)\"}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"auctionUrl\":\"https://\"}").andExpect(status().isBadRequest());
        patchOrder(order.id(), "{\"auctionUrl\":\"https://www.iaai.com/VehicleDetail/42~US\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auctionUrl").value("https://www.iaai.com/VehicleDetail/42~US"));
        patchOrder(order.id(), "{\"auctionUrl\":null}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auctionUrl").doesNotExist());
    }

    @Test
    void listFiltersAndSearches() throws Exception {
        Created audi = createOrder(dealerA(), Map.of("source", "COPART", "lotNumber", "111", "make", "Audi",
            "clientName", "Anna Nowak"));
        createOrder(dealerA(), Map.of("source", "COPART", "lotNumber", "222", "make", "BMW", "vin", "1HGCM82633A004352"));
        addEvent(audi.id(), "IN_TRANSIT", Instant.now());

        mvc.perform(get("/import-orders").param("q", "nowak").with(dealerA()))
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].make").value("Audi"));
        mvc.perform(get("/import-orders").param("q", "a004352").with(dealerA()))
            .andExpect(jsonPath("$.content[0].make").value("BMW"));
        mvc.perform(get("/import-orders").param("status", "IN_TRANSIT").with(dealerA()))
            .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/import-orders").param("q", "%").with(dealerA()))
            .andExpect(jsonPath("$.content", hasSize(0)));

        mvc.perform(post("/import-orders/{id}/archive", audi.id()).with(dealerA()))
            .andExpect(jsonPath("$.archivedAt").exists());
        mvc.perform(get("/import-orders").with(dealerA())).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/import-orders").param("archived", "true").with(dealerA()))
            .andExpect(jsonPath("$.content[0].id").value(audi.id().toString()));
    }

    @Test
    void deleteRemovesTheOrderAndItsRows() throws Exception {
        Created order = createOrder(dealerA());
        addEvent(order.id(), "WON", Instant.now());

        mvc.perform(delete("/import-orders/{id}", order.id()).with(dealerA())).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from import_orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from import_order_events", Integer.class)).isZero();
        mvc.perform(get("/import-orders/{id}", order.id()).with(dealerA())).andExpect(status().isNotFound());
    }

    @Test
    void malformedInputIsA400() throws Exception {
        mvc.perform(get("/import-orders/{id}", "not-a-uuid").with(dealerA())).andExpect(status().isBadRequest());
        mvc.perform(get("/import-orders").param("status", "FLYING").with(dealerA())).andExpect(status().isBadRequest());
        mvc.perform(post("/import-orders").with(dealerA()).contentType(MediaType.APPLICATION_JSON).content("[1]"))
            .andExpect(status().isBadRequest());
    }

    private ResultActions addEvent(UUID orderId, String status, Instant occurredAt) throws Exception {
        return mvc.perform(post("/import-orders/{id}/events", orderId).with(dealerA())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("status", status, "occurredAt", occurredAt.toString(),
                "clientNote", "Notatka " + status, "internalNote", "Wewnętrzna " + status))));
    }

    private ResultActions patchOrder(UUID orderId, String body) throws Exception {
        return mvc.perform(patch("/import-orders/{id}", orderId).with(dealerA())
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Id of the (only) event with the given status in the response. */
    private String eventId(ResultActions result, String status) throws Exception {
        for (JsonNode event : body(result.andReturn()).get("events")) {
            if (status.equals(event.get("status").asText())) return event.get("id").asText();
        }
        throw new AssertionError("no " + status + " event");
    }
}
