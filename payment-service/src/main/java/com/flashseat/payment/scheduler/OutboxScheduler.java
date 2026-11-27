package com.flashseat.payment.scheduler;

import com.flashseat.payment.service.OutboxPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "outbox.publisher.enabled",
        havingValue = "true"
)
public class OutboxScheduler {

    private final OutboxPublisher outboxPublisher;

    @Scheduled(
            fixedDelayString = "${outbox.publisher.fixed-delay-ms:2000}",
            initialDelayString = "${outbox.publisher.initial-delay-ms:5000}"
    )
    public void publishPendingEvents() {

        try {

            outboxPublisher.publishPendingEvents();

        } catch (RuntimeException ex) {

            log.error("Outbox publishing cycle failed", ex);
        }
    }
}