package com.trustlayer.service;

import com.trustlayer.config.ScannerProperties;
import com.trustlayer.entity.enums.ScanType;
import com.trustlayer.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanSubmissionRateLimiterTest {

    @Test
    void blocksTheNextSubmissionInsideTheOneMinuteWindowAndAllowsItAfterExpiry() {
        ScanSubmissionRateLimiter limiter = new ScanSubmissionRateLimiter(new ScannerProperties(2));
        Instant now = Instant.parse("2026-11-16T12:00:00Z");

        limiter.assertSubmissionAllowed(42L, ScanType.URL, now);
        limiter.assertSubmissionAllowed(42L, ScanType.URL, now.plusSeconds(10));

        assertThatThrownBy(() -> limiter.assertSubmissionAllowed(42L, ScanType.URL, now.plusSeconds(20)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode()).isEqualTo("SCAN_RATE_LIMITED"));

        limiter.assertSubmissionAllowed(42L, ScanType.URL, now.plusSeconds(61));
        limiter.assertSubmissionAllowed(42L, ScanType.QR, now.plusSeconds(20));
    }
}
