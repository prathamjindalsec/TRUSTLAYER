package com.trustlayer.service;

import com.trustlayer.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    @Test
    void blocksAfterFiveFailuresAndAllowsRetryAfterTheBlockExpires() {
        LoginAttemptService service = new LoginAttemptService();
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        String key = "person@example.test|127.0.0.1";

        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordFailure(key, now);
        }

        assertThatThrownBy(() -> service.assertAllowed(key, now.plusSeconds(1)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getCode()).isEqualTo("LOGIN_TEMPORARILY_BLOCKED");
                });

        service.assertAllowed(key, now.plus(Duration.ofMinutes(15)).plusSeconds(1));
    }
}
