package com.trustlayer.dto.user;

import com.trustlayer.entity.AuditLog;
import com.trustlayer.entity.enums.AuditOutcome;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

/**
 * A user's own sign-in audit records. Source addresses are intentionally masked in the API;
 * full values remain restricted to the audit database for incident-response use.
 */
public record LoginHistoryResponse(
        String eventType,
        AuditOutcome outcome,
        String maskedSourceAddress,
        String userAgent,
        Instant occurredAt
) {

    public static LoginHistoryResponse from(AuditLog auditLog) {
        return new LoginHistoryResponse(
                auditLog.getEventType(),
                auditLog.getOutcome(),
                maskAddress(auditLog.getIpAddress()),
                auditLog.getUserAgent(),
                auditLog.getCreatedAt()
        );
    }

    private static String maskAddress(byte[] address) {
        if (address == null) {
            return null;
        }
        if (address.length == 4) {
            return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + ".*";
        }
        try {
            String value = InetAddress.getByAddress(address).getHostAddress();
            int separator = value.lastIndexOf(':');
            return separator >= 0 ? value.substring(0, separator + 1) + "*" : "*";
        } catch (UnknownHostException ignored) {
            return "*";
        }
    }
}
