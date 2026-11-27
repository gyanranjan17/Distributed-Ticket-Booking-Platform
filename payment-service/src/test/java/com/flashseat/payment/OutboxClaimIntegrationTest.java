package com.flashseat.payment;

import com.flashseat.payment.entity.*;
import com.flashseat.payment.repository.OutboxEventRepository;
import com.flashseat.payment.service.OutboxClaimService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OutboxClaimIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired OutboxEventRepository repository;
    @Autowired OutboxClaimService claims;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() { repository.deleteAllInBatch(); }

    private OutboxEvent insert() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("PAYMENT");
        event.setAggregateId(1L);
        event.setEventType("PaymentSucceeded");
        event.setTopic("payment-events");
        event.setPartitionKey("1");
        event.setPayload("{}");
        event.setNextAttemptAt(Instant.now().minusSeconds(10));
        return repository.saveAndFlush(event);
    }

    @Test void onlyOneWorkerClaimsAnEvent() throws Exception {
        insert();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return claims.claimNext().isPresent();
                }));
            }
            start.countDown();
            int winners = 0;
            for (var result : results) if (result.get(15, TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners);
        }
    }

    @Test void expiredLeaseCanBeReclaimedAndOldTokenRejected() {
        insert();
        var first = claims.claimNext().orElseThrow();
        assertTrue(claims.claimNext().isEmpty());
        jdbc.update("UPDATE outbox_events SET lease_expires_at = clock_timestamp() - interval '1 second' WHERE id = ?", first.getId());
        var second = claims.claimNext().orElseThrow();
        assertNotEquals(first.getClaimToken(), second.getClaimToken());
        assertFalse(claims.complete(first));
        assertFalse(claims.retry(first, 2));
        assertTrue(claims.complete(second));
        var saved = repository.findById(first.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, saved.getStatus());
        assertNull(saved.getClaimToken());
        assertNull(saved.getLeaseExpiresAt());
        assertNotNull(saved.getPublishedAt());
    }

    @Test void retryReleasesClaimAndDelaysEligibility() {
        insert();
        var event = claims.claimNext().orElseThrow();
        assertTrue(claims.retry(event, 60));
        assertTrue(claims.claimNext().isEmpty());
        var saved = repository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(1, saved.getRetryCount());
        assertNull(saved.getClaimToken());
        assertNull(saved.getLeaseExpiresAt());
        jdbc.update("UPDATE outbox_events SET next_attempt_at = clock_timestamp() - interval '1 second' WHERE id = ?", event.getId());
        assertTrue(claims.claimNext().isPresent());
    }
}
