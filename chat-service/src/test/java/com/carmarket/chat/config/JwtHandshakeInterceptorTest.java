package com.carmarket.chat.config;

import com.carmarket.chat.security.JwtUtil;
import com.carmarket.chat.service.BlockedUserService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtHandshakeInterceptorTest {

    private final JwtUtil jwt = mock(JwtUtil.class);
    private final BlockedUserService blockedUsers = mock(BlockedUserService.class);
    private final JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(jwt, blockedUsers);

    private boolean handshake(Map<String, Object> attributes) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setQueryString("token=tok");
        return interceptor.beforeHandshake(new ServletServerHttpRequest(request),
            new ServletServerHttpResponse(new MockHttpServletResponse()), mock(WebSocketHandler.class), attributes);
    }

    @Test
    void blockedAccountsCantConnect() {
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.validateAndGetClaims("tok")).thenReturn(Jwts.claims().subject("u-1").build());
        when(blockedUsers.isBlocked("u-1")).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake(attributes)).isFalse();
        assertThat(attributes).doesNotContainKey("userId");
    }

    @Test
    void activeAccountsConnect() {
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.validateAndGetClaims("tok")).thenReturn(Jwts.claims().subject("u-2").build());
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake(attributes)).isTrue();
        assertThat(attributes).containsEntry("userId", "u-2");
    }
}
