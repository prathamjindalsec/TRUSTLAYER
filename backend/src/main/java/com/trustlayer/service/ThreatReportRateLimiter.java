package com.trustlayer.service;

import com.trustlayer.config.ThreatIntelligenceProperties;
import com.trustlayer.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Per-instance abuse guard for community submissions. */
@Service
public class ThreatReportRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ThreatIntelligenceProperties properties;
    private final ConcurrentMap<Long, Deque<Instant>> reportsByUser = new ConcurrentHashMap<>();

    public ThreatReportRateLimiter(ThreatIntelligenceProperties properties) {
        this.properties = properties;
    }

    public void assertReportAllowed(Long userId, Instant now) {
        Deque<Instant> reports = reportsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (reports) {
            Instant threshold = now.minus(WINDOW);
            while (!reports.isEmpty() && !reports.peekFirst().isAfter(threshold)) {
                reports.removeFirst();
            }
            if (reports.size() >= properties.maxReportsPerMinute()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "THREAT_REPORT_RATE_LIMITED",
                        "Too many community reports were submitted. Try again in a minute.");
            }
            reports.addLast(now);
        }
    }
}
