package com.carmarket.importorder.security;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * What the access log keeps about a client request: a truncated IP (IPv4 /24, IPv6 /48) and at
 * most 200 characters of the user agent. Full IPs are never stored.
 */
public record ClientRequestInfo(String ipPrefix, String userAgent) {

    private static final int USER_AGENT_MAX = 200;
    /** Only IP literals: InetAddress.getByName would do a DNS lookup for anything else. */
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    public static ClientRequestInfo from(HttpServletRequest request, int trustedProxies) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > USER_AGENT_MAX) {
            userAgent = userAgent.substring(0, USER_AGENT_MAX);
        }
        return new ClientRequestInfo(ipPrefix(clientIp(request, trustedProxies)), userAgent);
    }

    /**
     * The gateway appends the caller's address to X-Forwarded-For, so the last entry is the one it
     * saw. With {@code trustedProxies} reverse proxies in front of the gateway, each of them appended
     * one entry before it, so the client is at index {@code len - 1 - trustedProxies}; anything further
     * left is whatever the client sent and can't be trusted. Out of range: the last entry.
     */
    static String clientIp(HttpServletRequest request, int trustedProxies) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            int index = parts.length - 1 - trustedProxies;
            return (index >= 0 && index < parts.length ? parts[index] : parts[parts.length - 1]).trim();
        }
        return request.getRemoteAddr();
    }

    static String ipPrefix(String ip) {
        if (ip == null || !IP_LITERAL.matcher(ip).matches()) return null;
        try {
            InetAddress address = InetAddress.getByName(ip);
            byte[] b = address.getAddress();
            if (address instanceof Inet4Address) {
                return (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + ".0/24";
            }
            if (address instanceof Inet6Address) {
                return String.format("%x:%x:%x::/48",
                    ((b[0] & 0xFF) << 8) | (b[1] & 0xFF),
                    ((b[2] & 0xFF) << 8) | (b[3] & 0xFF),
                    ((b[4] & 0xFF) << 8) | (b[5] & 0xFF));
            }
        } catch (UnknownHostException e) {
            return null;
        }
        return null;
    }
}
