package com.flashseat.payment.service;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OutboxRetryPolicy {

    private static final long INITIAL_DELAY_SECONDS = 2;

    private static final long MAX_DELAY_SECONDS = 300;

    public Duration calculateDelay(int retryCount) {

        if (retryCount < 1) {
            throw new IllegalArgumentException(
                    "Retry count must be at least 1"
            );
        }

        int exponent = Math.min(retryCount - 1, 30);

        long delay = INITIAL_DELAY_SECONDS * (1L << exponent);

        return Duration.ofSeconds(
                Math.min(delay, MAX_DELAY_SECONDS)
        );
    }
}