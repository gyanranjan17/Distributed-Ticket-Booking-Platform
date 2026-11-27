package com.flashseat.payment;

import com.flashseat.payment.entity.OutboxEvent;
import com.flashseat.payment.entity.OutboxStatus;
import com.flashseat.payment.repository.OutboxEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OutboxEventRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
    }

    private OutboxEvent createEvent(
            OutboxStatus status,
            Instant createdAt
    ) {

        OutboxEvent event = new OutboxEvent();

        event.setId(UUID.randomUUID());
        event.setAggregateType("PAYMENT");
        event.setAggregateId(101L);
        event.setEventType("PaymentSucceeded");
        event.setTopic("payment-events");
        event.setPartitionKey("101");
        event.setPayload("{\"paymentId\":101}");
        event.setStatus(status);
        event.setRetryCount(0);
        event.setCreatedAt(createdAt);

        if (status == OutboxStatus.PUBLISHED) {
            event.setPublishedAt(createdAt);
        }

        return event;
    }

    private OutboxEvent savePendingEvent() {

        return outboxEventRepository.saveAndFlush(
                createEvent(
                        OutboxStatus.PENDING,
                        Instant.now()
                )
        );
    }

    @Test
    void shouldFetchOnlyPendingEvents() {

        outboxEventRepository.saveAndFlush(
                createEvent(
                        OutboxStatus.PENDING,
                        Instant.now()
                )
        );

        outboxEventRepository.saveAndFlush(
                createEvent(
                        OutboxStatus.PUBLISHED,
                        Instant.now()
                )
        );

        List<OutboxEvent> events =
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAscIdAsc(
                                OutboxStatus.PENDING
                        );

        assertEquals(1, events.size());
        assertEquals(
                OutboxStatus.PENDING,
                events.getFirst().getStatus()
        );
    }

    @Test
    void shouldFetchOldestEventsFirst() {

        Instant now = Instant.now();

        OutboxEvent newest =
                outboxEventRepository.saveAndFlush(
                        createEvent(
                                OutboxStatus.PENDING,
                                now
                        )
                );

        OutboxEvent oldest =
                outboxEventRepository.saveAndFlush(
                        createEvent(
                                OutboxStatus.PENDING,
                                now.minusSeconds(120)
                        )
                );

        OutboxEvent middle =
                outboxEventRepository.saveAndFlush(
                        createEvent(
                                OutboxStatus.PENDING,
                                now.minusSeconds(60)
                        )
                );

        List<OutboxEvent> events =
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAscIdAsc(
                                OutboxStatus.PENDING
                        );

        assertEquals(3, events.size());

        assertEquals(oldest.getId(), events.get(0).getId());
        assertEquals(middle.getId(), events.get(1).getId());
        assertEquals(newest.getId(), events.get(2).getId());
    }

    @Test
    void shouldFetchMaximum100Events() {

        List<OutboxEvent> events = new ArrayList<>();

        Instant now = Instant.now();

        for (int i = 0; i < 105; i++) {

            events.add(
                    createEvent(
                            OutboxStatus.PENDING,
                            now.plusSeconds(i)
                    )
            );
        }

        outboxEventRepository.saveAllAndFlush(events);

        List<OutboxEvent> result =
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAscIdAsc(
                                OutboxStatus.PENDING
                        );

        assertEquals(105, outboxEventRepository.count());
        assertEquals(100, result.size());

        assertEquals(
                events.getFirst().getId(),
                result.getFirst().getId()
        );

        assertEquals(
                events.get(99).getId(),
                result.getLast().getId()
        );
    }

    @Test
    void shouldMarkPendingEventAsPublished() {

        OutboxEvent event = savePendingEvent();

        Instant publishedAt =
                Instant.parse("2026-11-27T12:00:00Z");

        int updatedRows = transactionTemplate.execute(status ->
                outboxEventRepository.markAsPublished(
                        event.getId(),
                        OutboxStatus.PENDING,
                        OutboxStatus.PUBLISHED,
                        publishedAt
                )
        );

        assertEquals(1, updatedRows);

        OutboxEvent updated = outboxEventRepository
                .findById(event.getId())
                .orElseThrow();

        assertEquals(OutboxStatus.PUBLISHED, updated.getStatus());
        assertEquals(publishedAt, updated.getPublishedAt());
    }

    @Test
    void shouldNotPublishAlreadyPublishedEventAgain() {

        OutboxEvent event = savePendingEvent();

        Instant firstPublishedAt =
                Instant.parse("2026-11-27T12:00:00Z");

        Instant secondPublishedAt =
                firstPublishedAt.plusSeconds(60);

        int firstUpdate = transactionTemplate.execute(status ->
                outboxEventRepository.markAsPublished(
                        event.getId(),
                        OutboxStatus.PENDING,
                        OutboxStatus.PUBLISHED,
                        firstPublishedAt
                )
        );

        int secondUpdate = transactionTemplate.execute(status ->
                outboxEventRepository.markAsPublished(
                        event.getId(),
                        OutboxStatus.PENDING,
                        OutboxStatus.PUBLISHED,
                        secondPublishedAt
                )
        );

        assertEquals(1, firstUpdate);
        assertEquals(0, secondUpdate);

        OutboxEvent updated = outboxEventRepository
                .findById(event.getId())
                .orElseThrow();

        assertEquals(
                firstPublishedAt,
                updated.getPublishedAt()
        );
    }

    @Test
    void shouldNotIncrementRetryCountForPublishedEvent() {

        OutboxEvent event = savePendingEvent();

        transactionTemplate.executeWithoutResult(status ->
                outboxEventRepository.markAsPublished(
                        event.getId(),
                        OutboxStatus.PENDING,
                        OutboxStatus.PUBLISHED,
                        Instant.now()
                )
        );

        int updatedRows = transactionTemplate.execute(status ->
                outboxEventRepository.incrementRetryCount(
                        event.getId(),
                        OutboxStatus.PENDING
                )
        );

        assertEquals(0, updatedRows);

        OutboxEvent updated = outboxEventRepository
                .findById(event.getId())
                .orElseThrow();

        assertEquals(0, updated.getRetryCount());
    }

    @Test
    void shouldIncrementRetryCountAtomicallyUnderConcurrency()
            throws Exception {

        OutboxEvent event = savePendingEvent();

        int threadCount = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<Integer>> futures = new ArrayList<>();

        try {

            for (int i = 0; i < threadCount; i++) {

                futures.add(executor.submit(() -> {

                    readyLatch.countDown();
                    startLatch.await();

                    return transactionTemplate.execute(status ->
                            outboxEventRepository.incrementRetryCount(
                                    event.getId(),
                                    OutboxStatus.PENDING
                            )
                    );
                }));
            }

            assertTrue(
                    readyLatch.await(10, TimeUnit.SECONDS)
            );

            startLatch.countDown();

            for (Future<Integer> future : futures) {

                assertEquals(
                        1,
                        future.get(30, TimeUnit.SECONDS)
                );
            }

            OutboxEvent updated = outboxEventRepository
                    .findById(event.getId())
                    .orElseThrow();

            assertEquals(10, updated.getRetryCount());

        } finally {

            startLatch.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}