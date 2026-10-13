package com.carmarket.chat.config;

import com.carmarket.chat.controller.ChatRestController;
import com.carmarket.chat.security.GatewaySignatureFilter;
import com.carmarket.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real SecurityConfig + GatewaySignatureFilter: port 8085 is published, so X-User-Id alone must not work. */
@WebMvcTest(ChatRestController.class)
@Import({SecurityConfig.class, GatewaySignatureFilter.class})
@TestPropertySource(properties = "gateway.internal-secret=" + SecurityRulesTest.SECRET)
class SecurityRulesTest {

    static final String SECRET = "test-gateway-secret";
    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ChatService chatService;

    @Test
    void forgedUserIdWithoutSignatureIsRejected() throws Exception {
        mvc.perform(get("/chat/conversations").header("X-User-Id", USER.toString()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/chat/conversations").header("X-User-Id", USER.toString())
                .header("X-Gateway-Signature", System.currentTimeMillis() + ":forged"))
            .andExpect(status().isForbidden());
        verify(chatService, never()).getMyConversations(any());
    }

    @Test
    void staleSignatureIsRejected() throws Exception {
        mvc.perform(get("/chat/conversations").header("X-User-Id", USER.toString())
                .header("X-Gateway-Signature", sign(USER.toString(), System.currentTimeMillis() - 60_000)))
            .andExpect(status().isForbidden());
    }

    @Test
    void signedRequestReachesTheController() throws Exception {
        when(chatService.getMyConversations(USER)).thenReturn(List.of());

        mvc.perform(get("/chat/conversations").header("X-User-Id", USER.toString())
                .header("X-User-Roles", "USER")
                .header("X-Gateway-Signature", sign(USER.toString(), System.currentTimeMillis())))
            .andExpect(status().isOk());
        verify(chatService).getMyConversations(USER);
    }

    static String sign(String userId, long ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal((userId + ":" + ts).getBytes(StandardCharsets.UTF_8));
        return ts + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }
}
