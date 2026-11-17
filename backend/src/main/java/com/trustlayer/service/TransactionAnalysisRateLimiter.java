package com.trustlayer.service;

import com.trustlayer.config.TransactionProperties;
import com.trustlayer.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Per-instance abuse guard for simulated transaction analyses. */
@Service
public class TransactionAnalysisRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final TransactionProperties properties;
    private final ConcurrentMap<Long, Deque<Instant>> submissionsByUser = new ConcurrentHashMap<>();

    public TransactionAnalysisRateLimiter(TransactionProperties properties) {
        this.properties = properties;
    }

    public void assertSubmissionAllowed(Long userId, Instant now) {
        Deque<Instant> submissions = submissionsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (submissions) {
            Instant threshold = now.minus(WINDOW);
            while (!submissions.isEmpty() && !submissions.peekFirst().isAfter(threshold)) {
                submissions.removeFirst();
            }
            if (submissions.size() >= properties.maxAnalysesPerMinute()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TRANSACTION_ANALYSIS_RATE_LIMITED",
                        "Too many simulated transaction analyses were submitted. Try again in a minute.");
            }
            submissions.addLast(now);
        }
    }
}
