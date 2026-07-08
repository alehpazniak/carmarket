package com.carmarket.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Verifies the gateway HMAC signature and populates the SecurityContext from X-User-Id. */
@Slf4j
@Component
public class GatewaySignatureFilter extends OncePerRequestFilter {

    private static final long MAX_AGE_MS = 30_000; // reject headers older than 30s (replay window)

    private final SecretKeySpec keySpec;

    public GatewaySignatureFilter(@Value("${gateway.internal-secret}") String internalSecret) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("gateway.internal-secret must be set");
        }
        this.keySpec = new SecretKeySpec(
            internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String signature = request.getHeader("X-Gateway-Signature");

        if (userId != null && signature != null && verify(userId, signature)) {
            String rolesHeader = request.getHeader("X-User-Roles");
            List<SimpleGrantedAuthority> authorities = (rolesHeader == null || rolesHeader.isBlank())
                ? List.of()
                : Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        // No valid signature → no authentication set → Spring Security rejects protected routes
        chain.doFilter(request, response);
    }

    private boolean verify(String userId, String signatureHeader) {
        try {
            int idx = signatureHeader.indexOf(':');
            if (idx < 0) return false;

            long ts = Long.parseLong(signatureHeader.substring(0, idx));
            String providedSig = signatureHeader.substring(idx + 1);

            if (System.currentTimeMillis() - ts > MAX_AGE_MS) {
                log.warn("Gateway signature expired for user {}", userId);
                return false;
            }

            String expectedSig = hmac(userId + ":" + ts);
            // constant-time compare
            return MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                providedSig.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Gateway signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

}
