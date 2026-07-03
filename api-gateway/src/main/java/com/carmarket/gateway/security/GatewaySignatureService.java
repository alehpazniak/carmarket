package com.carmarket.gateway.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class GatewaySignatureService {

    @Value("${gateway.internal-secret}")
    private String internalSecret;

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("gateway.internal-secret must be set");
        }
        this.keySpec = new SecretKeySpec(
            internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String sign(String userId) {
        long ts = System.currentTimeMillis();
        String payload = userId + ":" + ts;
        return ts + ":" + hmac(payload);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign header", e);
        }
    }
}
