package com.carmarket.importorder.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRequestInfoTest {

    @Test
    void truncatesIpv4To24AndIpv6To48() {
        assertThat(ClientRequestInfo.ipPrefix("203.0.113.57")).isEqualTo("203.0.113.0/24");
        assertThat(ClientRequestInfo.ipPrefix("2001:db8:85a3:8d3:1319:8a2e:370:7348")).isEqualTo("2001:db8:85a3::/48");
        assertThat(ClientRequestInfo.ipPrefix("::1")).isEqualTo("0:0:0::/48");
    }

    @Test
    void neverResolvesHostNames() {
        assertThat(ClientRequestInfo.ipPrefix("example.com")).isNull();
        assertThat(ClientRequestInfo.ipPrefix(null)).isNull();
    }

    @Test
    void usesTheLastForwardedForEntryAddedByTheGateway() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.23");
        request.addHeader("User-Agent", "x".repeat(500));

        ClientRequestInfo info = ClientRequestInfo.from(request);

        assertThat(info.ipPrefix()).isEqualTo("198.51.100.0/24");
        assertThat(info.userAgent()).hasSize(200);
    }

    @Test
    void fallsBackToTheRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.77");

        assertThat(ClientRequestInfo.from(request).ipPrefix()).isEqualTo("192.168.1.0/24");
    }
}
