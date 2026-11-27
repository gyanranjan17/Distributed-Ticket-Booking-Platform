package com.flashseat.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @PrePersist
    public void onCreate() {

        if (status == null) {
            status = OutboxStatus.PENDING;
        }

        if (retryCount == null) {
            retryCount = 0;
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }
}