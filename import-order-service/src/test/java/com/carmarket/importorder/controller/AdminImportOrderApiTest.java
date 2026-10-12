package com.carmarket.importorder.controller;

import com.carmarket.importorder.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminImportOrderApiTest extends IntegrationTestBase {

    @Test
    void adminsFilterByDealerAndDealersAlwaysSeeTheirOwn() throws Exception {
        createOrder(dealerA());
        createOrder(dealerA());
        createOrder(dealerB());

        mvc.perform(get("/import-orders").with(admin()))
            .andExpect(jsonPath("$.content", hasSize(3)));
        mvc.perform(get("/import-orders").param("dealerId", DEALER_B.toString()).with(admin()))
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].dealerId", is(DEALER_B.toString())));
        // A dealer can't widen their view with someone else's id
        mvc.perform(get("/import-orders").param("dealerId", DEALER_B.toString()).with(dealerA()))
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].dealerId", is(DEALER_A.toString())));
    }

    @Test
    void statsAreAdminOnly() throws Exception {
        createOrder(dealerA());
        createOrder(dealerB());

        mvc.perform(get("/import-orders/admin/stats").with(dealerA())).andExpect(status().isForbidden());
        mvc.perform(get("/import-orders/admin/stats").with(plainUser())).andExpect(status().isForbidden());
        mvc.perform(get("/import-orders/admin/stats").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", is(2)))
            .andExpect(jsonPath("$.open", is(2)))
            .andExpect(jsonPath("$.dealers", is(2)))
            .andExpect(jsonPath("$.newLast30Days", is(2)))
            .andExpect(jsonPath("$.daily", hasSize(30)));
    }
}
