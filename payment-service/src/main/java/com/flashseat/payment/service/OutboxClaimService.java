package com.flashseat.payment.service;

import com.flashseat.payment.entity.OutboxEvent;
import com.flashseat.payment.entity.OutboxStatus;
import com.flashseat.payment.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxEventRepository repository;

    private static final long LEASE_DURATION_SECONDS = 90;

    @Transactional
    public Optional<OutboxEvent> claimNext() {

        return repository.lockNextEligibleEvent().map(event -> {

            Instant leaseExpiresAt = repository.databaseTime()
                    .plusSeconds(LEASE_DURATION_SECONDS);

            UUID claimToken = UUID.randomUUID();

            event.setStatus(OutboxStatus.PROCESSING);
            event.setClaimToken(claimToken);
            event.setLeaseExpiresAt(leaseExpiresAt);

            return event;
        });
    }

    @Transactional
    public boolean complete(OutboxEvent event) {

        return repository.completeClaim(
                event.getId(),
                event.getClaimToken()
        ) == 1;
    }

    @Transactional
    public boolean retry(OutboxEvent event, long delaySeconds) {

        return repository.retryClaim(
                event.getId(),
                event.getClaimToken(),
                delaySeconds
        ) == 1;
    }
}