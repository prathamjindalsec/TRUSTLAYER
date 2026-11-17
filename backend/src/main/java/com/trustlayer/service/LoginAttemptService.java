package com.trustlayer.service;

import com.trustlayer.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A conservative single-instance login throttle. Production multi-node deployment must replace
 * this with a shared rate-limit store such as Redis or an edge gateway control.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void assertAllowed(String key, Instant now) {
        AttemptState current = attempts.get(key);
        if (current != null && current.blockedUntil() != null && current.blockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_TEMPORARILY_BLOCKED",
                    "Too many failed login attempts. Try again later.");
        }
        if (current != null && current.blockedUntil() != null && !current.blockedUntil().isAfter(now)) {
            attempts.remove(key, current);
        }
    }

    public void recordFailure(String key, Instant now) {
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.firstFailure().plus(WINDOW).isBefore(now)) {
                return new AttemptState(1, now, null);
            }

            int failures = current.failures() + 1;
            Instant blockedUntil = failures >= MAX_FAILURES ? now.plus(BLOCK_DURATION) : null;
            return new AttemptState(failures, current.firstFailure(), blockedUntil);
        });
    }

    public void clear(String key) {
        attempts.remove(key);
    }

    private record AttemptState(int failures, Instant firstFailure, Instant blockedUntil) {
    }
}
