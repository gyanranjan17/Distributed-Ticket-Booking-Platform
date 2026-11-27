package com.flashseat.payment.repository;

import com.flashseat.payment.entity.OutboxEvent;
import com.flashseat.payment.entity.OutboxStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAscIdAsc(
            OutboxStatus status
    );

    List<OutboxEvent>
    findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAscIdAsc(
            OutboxStatus status,
            Instant currentTime
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE OutboxEvent e
        SET e.status = :publishedStatus,
            e.publishedAt = :publishedAt
        WHERE e.id = :eventId
        AND e.status = :pendingStatus
        """)
    int markAsPublished(
            @Param("eventId") UUID eventId,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("publishedStatus") OutboxStatus publishedStatus,
            @Param("publishedAt") Instant publishedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE OutboxEvent e
        SET e.retryCount = e.retryCount + 1
        WHERE e.id = :eventId
        AND e.status = :pendingStatus
        """)
    int incrementRetryCount(
            @Param("eventId") UUID eventId,
            @Param("pendingStatus") OutboxStatus pendingStatus
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
    UPDATE OutboxEvent e
    SET e.retryCount = e.retryCount + 1,
        e.nextAttemptAt = :nextAttemptAt
    WHERE e.id = :eventId
    AND e.status = :pendingStatus
    AND e.retryCount = :expectedRetryCount
    """)
    int scheduleRetry(
            @Param("eventId") UUID eventId,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("expectedRetryCount") Integer expectedRetryCount,
            @Param("nextAttemptAt") Instant nextAttemptAt
    );
    @Query(value = """
        SELECT * FROM outbox_events
        WHERE (status = 'PENDING' AND next_attempt_at <= clock_timestamp())
           OR (status = 'PROCESSING' AND lease_expires_at <= clock_timestamp())
        ORDER BY next_attempt_at, created_at, id
        LIMIT 1 FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    java.util.Optional<OutboxEvent> lockNextEligibleEvent();

    @Query(value = "SELECT clock_timestamp()", nativeQuery = true)
    Instant databaseTime();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE outbox_events SET status = 'PUBLISHED', published_at = clock_timestamp(),
            claim_token = NULL, lease_expires_at = NULL
        WHERE id = :id AND status = 'PROCESSING' AND claim_token = :token
        """, nativeQuery = true)
    int completeClaim(@Param("id") UUID id, @Param("token") UUID token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE outbox_events SET status = 'PENDING', retry_count = retry_count + 1,
            next_attempt_at = clock_timestamp() + (:delaySeconds * interval '1 second'),
            claim_token = NULL, lease_expires_at = NULL
        WHERE id = :id AND status = 'PROCESSING' AND claim_token = :token
        """, nativeQuery = true)
    int retryClaim(@Param("id") UUID id, @Param("token") UUID token,
                   @Param("delaySeconds") long delaySeconds);
}
