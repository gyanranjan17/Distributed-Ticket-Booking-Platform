package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.entity.OutboxEvent;
import com.flashseat.payment.entity.OutboxStatus;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.event.PaymentSucceededEvent;
import com.flashseat.payment.repository.OutboxEventRepository;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.OutboxEventService;
import com.flashseat.payment.service.PaymentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PaymentOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingClient bookingClient;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
    }

    private Payment createPendingPayment() {

        Payment payment = new Payment();

        payment.setBookingId(101L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(UUID.randomUUID().toString());

        return paymentRepository.saveAndFlush(payment);
    }

    @Test
    void shouldCreateOutboxEventForSuccessfulPayment() {

        Payment payment = createPendingPayment();

        paymentService.confirmPayment(
                payment.getId(), "TXN-SUCCESS-001"
        );

        assertEquals(1, outboxEventRepository.count());

        OutboxEvent event =
                outboxEventRepository.findAll().getFirst();

        assertEquals("PAYMENT", event.getAggregateType());
        assertEquals(payment.getId(), event.getAggregateId());
        assertEquals("PaymentSucceeded", event.getEventType());
        assertEquals("payment-events", event.getTopic());
        assertEquals("101", event.getPartitionKey());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertNotNull(event.getCreatedAt());
        assertNull(event.getPublishedAt());
    }

    @Test
    void shouldNotCreateSuccessEventForFailedPayment() {

        Payment payment = createPendingPayment();

        paymentService.failPayment(
                payment.getId(), "TXN-FAILED-001"
        );

        assertEquals(0, outboxEventRepository.count());

        Payment updated = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(PaymentStatus.FAILED, updated.getStatus());
    }

    @Test
    void shouldNotCreateDuplicateOutboxEvents() {

        Payment payment = createPendingPayment();

        paymentService.confirmPayment(
                payment.getId(), "TXN-DUPLICATE-001"
        );

        UUID originalEventId = outboxEventRepository
                .findAll().getFirst().getId();

        paymentService.confirmPayment(
                payment.getId(), "TXN-DUPLICATE-001"
        );

        assertEquals(1, outboxEventRepository.count());

        assertEquals(
                originalEventId,
                outboxEventRepository.findAll().getFirst().getId()
        );
    }

    @Test
    void shouldPreserveOutboxEventForConflictingCallback() {

        Payment payment = createPendingPayment();

        paymentService.confirmPayment(
                payment.getId(), "TXN-ORIGINAL-001"
        );

        OutboxEvent originalEvent =
                outboxEventRepository.findAll().getFirst();

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.failPayment(
                        payment.getId(), "TXN-ORIGINAL-001"
                )
        );

        assertEquals(1, outboxEventRepository.count());

        OutboxEvent currentEvent =
                outboxEventRepository.findAll().getFirst();

        assertEquals(originalEvent.getId(), currentEvent.getId());
        assertEquals(
                originalEvent.getPayload(),
                currentEvent.getPayload()
        );
    }

    @Test
    void shouldStoreCorrectEventPayload() {

        Payment payment = createPendingPayment();

        paymentService.confirmPayment(
                payment.getId(), "TXN-PAYLOAD-001"
        );

        OutboxEvent outboxEvent =
                outboxEventRepository.findAll().getFirst();

        PaymentSucceededEvent event =
                objectMapper.readValue(
                        outboxEvent.getPayload(),
                        PaymentSucceededEvent.class
                );

        assertEquals(outboxEvent.getId(), event.eventId());
        assertEquals("PaymentSucceeded", event.eventType());
        assertEquals(1, event.eventVersion());
        assertEquals(payment.getId(), event.paymentId());
        assertEquals(payment.getBookingId(), event.bookingId());
        assertEquals(0, payment.getAmount().compareTo(event.amount()));
        assertEquals("TXN-PAYLOAD-001", event.transactionId());
        assertNotNull(event.occurredAt());
    }

    @Test
    void shouldRollbackPaymentAndOutboxEventTogether() {

        Payment payment = createPendingPayment();

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {

                    paymentService.confirmPayment(
                            payment.getId(), "TXN-ROLLBACK-001"
                    );

                    assertEquals(1, outboxEventRepository.count());

                    throw new IllegalStateException(
                            "Simulated transaction failure"
                    );
                })
        );

        Payment updated = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(PaymentStatus.PENDING, updated.getStatus());
        assertNull(updated.getTransactionId());
        assertEquals(0, outboxEventRepository.count());
    }

    @Test
    void shouldRejectOutboxCreationWithoutTransaction() {

        Payment payment = createPendingPayment();
        payment.setStatus(PaymentStatus.SUCCESS);

        assertThrows(
                IllegalTransactionStateException.class,
                () -> outboxEventService.recordPaymentSucceeded(payment)
        );

        assertEquals(0, outboxEventRepository.count());
    }

    @Test
    void shouldCreateOnlyOneOutboxEventUnderConcurrency()
            throws Exception {

        Payment payment = createPendingPayment();

        int threadCount = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        try {

            for (int i = 0; i < threadCount; i++) {

                futures.add(executor.submit(() -> {

                    readyLatch.countDown();
                    startLatch.await();

                    return paymentService.confirmPayment(
                            payment.getId(),
                            "TXN-CONCURRENT-001"
                    );
                }));
            }

            assertTrue(
                    readyLatch.await(10, TimeUnit.SECONDS)
            );

            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            assertEquals(1, outboxEventRepository.count());

            Payment updated = paymentRepository
                    .findById(payment.getId())
                    .orElseThrow();

            assertEquals(
                    PaymentStatus.SUCCESS,
                    updated.getStatus()
            );

            assertEquals(
                    "TXN-CONCURRENT-001",
                    updated.getTransactionId()
            );

        } finally {

            startLatch.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}