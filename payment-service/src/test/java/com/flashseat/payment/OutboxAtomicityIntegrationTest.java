package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.repository.OutboxEventRepository;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OutboxAtomicityIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

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
    void shouldRollbackPaymentWhenOutboxInsertionFails() {

        Payment payment = createPendingPayment();

        jdbcTemplate.execute("""
            ALTER TABLE outbox_events
            ADD CONSTRAINT test_reject_success_event
            CHECK (event_type <> 'PaymentSucceeded')
            """);

        try {

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> paymentService.confirmPayment(
                            payment.getId(),
                            "TXN-OUTBOX-FAILURE-001"
                    )
            );

            assertTrue(
                    containsExpectedConstraintViolation(exception),
                    "Expected PostgreSQL outbox check constraint violation"
            );

            Payment updated = paymentRepository
                    .findById(payment.getId())
                    .orElseThrow();

            assertEquals(
                    PaymentStatus.PENDING,
                    updated.getStatus()
            );

            assertNull(updated.getTransactionId());

            assertEquals(
                    0,
                    outboxEventRepository.count()
            );

        } finally {

            jdbcTemplate.execute("""
                ALTER TABLE outbox_events
                DROP CONSTRAINT IF EXISTS test_reject_success_event
                """);
        }

        // Verify that retrying after the failure succeeds.

        paymentService.confirmPayment(
                payment.getId(),
                "TXN-OUTBOX-FAILURE-001"
        );

        Payment recovered = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                recovered.getStatus()
        );

        assertEquals(
                1,
                outboxEventRepository.count()
        );
    }

    private boolean containsExpectedConstraintViolation(
            Throwable exception
    ) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof SQLException sqlException
                    && "23514".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(
                    "test_reject_success_event"
            )) {

                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}