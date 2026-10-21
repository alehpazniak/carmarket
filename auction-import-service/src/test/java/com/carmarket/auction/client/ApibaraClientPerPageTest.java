package com.carmarket.auction.client;

import com.carmarket.auction.config.ParserTargetsConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApibaraClientPerPageTest {

    private final ApibaraClient client = new ApibaraClient(new ParserTargetsConfig());

    @Test
    void configuredSizeUntilApibaraSendsItsLimitThenTheSmaller() {
        assertThat(client.effectivePerPage(50)).isEqualTo(50);

        client.rememberPerPageLimit("40");
        assertThat(client.effectivePerPage(50)).isEqualTo(40);
        assertThat(client.effectivePerPage(20)).isEqualTo(20);

        client.rememberPerPageLimit("garbage");
        client.rememberPerPageLimit(null);
        client.rememberPerPageLimit("0");
        assertThat(client.effectivePerPage(50)).isEqualTo(40);
    }
}
