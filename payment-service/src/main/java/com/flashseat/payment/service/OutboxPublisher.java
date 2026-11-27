package com.flashseat.payment.service;

import com.flashseat.payment.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private final OutboxClaimService claimService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRetryPolicy retryPolicy;

    public void publishPendingEvents() {
        for (int i = 0; i < 100 && !Thread.currentThread().isInterrupted(); i++) {
            var claimed = claimService.claimNext();
            if (claimed.isEmpty()) return;
            try {
                publishEvent(claimed.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Outbox publishing interrupted; lease will expire");
                return;
            }
        }
    }

    private void publishEvent(OutboxEvent event) throws InterruptedException {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            long delay = retryPolicy.calculateDelay(event.getRetryCount() + 1).toSeconds();
            if (claimService.retry(event, delay)) {
                log.warn("Outbox event {} failed; retry in {} seconds", event.getId(), delay, ex);
            }
            return;
        }
        if (claimService.complete(event)) {
            log.info("Published outbox event {}", event.getId());
        } else {
            log.warn("Acknowledged event {} but claim ownership changed", event.getId());
        }
    }
}
