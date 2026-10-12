package com.carmarket.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * The client's IP for the IP-keyed rate limits.
 * <p>
 * gateway.trusted-proxies = number of reverse proxies in front of the gateway. 0 (the default, and
 * right for local runs) takes the direct remote address and ignores X-Forwarded-For entirely, since the
 * client can send any value there. N &gt; 0 takes the N-th X-Forwarded-For entry from the right: each
 * trusted proxy appends the address it saw, so anything further left was written by the client.
 */
@Component
public class ClientIpResolver {

    private final RemoteAddressResolver delegate;

    public ClientIpResolver(@Value("${gateway.trusted-proxies:0}") int trustedProxies) {
        if (trustedProxies < 0) {
            throw new IllegalArgumentException("gateway.trusted-proxies must be >= 0, was " + trustedProxies);
        }
        this.delegate = trustedProxies == 0
            ? new RemoteAddressResolver() { }
            : XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxies);
    }

    public String resolve(ServerWebExchange exchange) {
        InetSocketAddress address = delegate.resolve(exchange);
        if (address == null) return "unknown";
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
    }
}
