package com.trustlayer.service;

import com.trustlayer.config.ScannerProperties;
import com.trustlayer.entity.enums.ScanType;
import com.trustlayer.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A bounded sliding-window limiter for authenticated scan submissions. Limits are independent
 * per user and scan type, so a burst of one analysis type does not consume another type's
 * allowance. This is deliberately a single-instance safeguard; a deployed multi-instance
 * service needs a shared gateway or Redis-backed limiter.
 */
@Service
public class ScanSubmissionRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ScannerProperties properties;
    private final ConcurrentMap<String, Deque<Instant>> submissionsByUserAndType = new ConcurrentHashMap<>();

    public ScanSubmissionRateLimiter(ScannerProperties properties) {
        this.properties = properties;
    }

    public void assertSubmissionAllowed(Long userId, ScanType scanType, Instant now) {
        String key = scanType.name() + ":" + userId;
        Deque<Instant> submissions = submissionsByUserAndType.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (submissions) {
            Instant threshold = now.minus(WINDOW);
            while (!submissions.isEmpty() && !submissions.peekFirst().isAfter(threshold)) {
                submissions.removeFirst();
            }
            if (submissions.size() >= properties.maxSubmissionsPerMinute()) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "SCAN_RATE_LIMITED",
                        "Too many " + scanType.name().toLowerCase() + " scans were submitted. Try again in a minute."
                );
            }
            submissions.addLast(now);
        }
    }
}
