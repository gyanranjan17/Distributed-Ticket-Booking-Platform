package com.flashseat.payment;

import com.flashseat.payment.entity.OutboxEvent;
import com.flashseat.payment.entity.OutboxStatus;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.event.PaymentSucceededEvent;
import com.flashseat.payment.repository.OutboxEventRepository;
import com.flashseat.payment.service.OutboxEventService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OutboxEventServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
    }

    private Payment createPayment(PaymentStatus status) {

        Payment payment = new Payment();

        payment.setId(101L);
        payment.setBookingId(501L);
        payment.setAmount(new BigDecimal("750.00"));
        payment.setStatus(status);
        payment.setTransactionId("TXN-OUTBOX-001");

        return payment;
    }

    @Test
    void shouldPersistPaymentSucceededEvent() {

        Payment payment = createPayment(
                PaymentStatus.SUCCESS
        );

        transactionTemplate.executeWithoutResult(status ->
                outboxEventService.recordPaymentSucceeded(payment)
        );

        assertEquals(1, outboxEventRepository.count());

        OutboxEvent event =
                outboxEventRepository.findAll().getFirst();

        assertNotNull(event.getId());
        assertEquals("PAYMENT", event.getAggregateType());
        assertEquals(101L, event.getAggregateId());
        assertEquals("501", event.getPartitionKey());
        assertEquals("payment-events", event.getTopic());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());

        PaymentSucceededEvent payload =
                objectMapper.readValue(
                        event.getPayload(),
                        PaymentSucceededEvent.class
                );

        assertEquals(event.getId(), payload.eventId());
        assertEquals(101L, payload.paymentId());
        assertEquals(501L, payload.bookingId());
        assertEquals("TXN-OUTBOX-001", payload.transactionId());

        assertEquals(
                0,
                new BigDecimal("750.00")
                        .compareTo(payload.amount())
        );
    }

    @Test
    void shouldRejectPendingPayment() {

        Payment payment = createPayment(
                PaymentStatus.PENDING
        );

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.executeWithoutResult(status ->
                        outboxEventService.recordPaymentSucceeded(payment)
                )
        );

        assertEquals(0, outboxEventRepository.count());
    }

    @Test
    void shouldRejectFailedPayment() {

        Payment payment = createPayment(
                PaymentStatus.FAILED
        );

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.executeWithoutResult(status ->
                        outboxEventService.recordPaymentSucceeded(payment)
                )
        );

        assertEquals(0, outboxEventRepository.count());
    }

    @Test
    void shouldRequireExistingTransaction() {

        Payment payment = createPayment(
                PaymentStatus.SUCCESS
        );

        assertThrows(
                IllegalTransactionStateException.class,
                () -> outboxEventService.recordPaymentSucceeded(payment)
        );

        assertEquals(0, outboxEventRepository.count());
    }
}