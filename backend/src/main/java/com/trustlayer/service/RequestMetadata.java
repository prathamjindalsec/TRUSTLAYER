package com.trustlayer.service;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

public record RequestMetadata(
        byte[] ipAddress,
        String userAgent
) {

    public static RequestMetadata from(HttpServletRequest request) {
        byte[] address = null;
        try {
            address = InetAddress.getByName(request.getRemoteAddr()).getAddress();
        } catch (UnknownHostException ignored) {
            // Audit metadata is optional; authentication must not fail because an address is unavailable.
        }

        String agent = request.getHeader("User-Agent");
        if (agent != null && agent.length() > 512) {
            agent = agent.substring(0, 512);
        }
        return new RequestMetadata(address, agent);
    }
}
