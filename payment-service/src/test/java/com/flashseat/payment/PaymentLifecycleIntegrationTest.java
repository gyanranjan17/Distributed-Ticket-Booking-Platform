package com.flashseat.payment;

import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;
import com.flashseat.payment.client.BookingClient;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PaymentLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private BookingClient bookingClient;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAllInBatch();
    }

    private Payment createPendingPayment() {

        Payment payment = new Payment();

        payment.setBookingId(101L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(
                UUID.randomUUID().toString()
        );

        return paymentRepository.saveAndFlush(payment);
    }

    @Test
    void shouldConfirmPendingPayment() {

        Payment payment = createPendingPayment();

        PaymentResponse response =
                paymentService.confirmPayment(
                        payment.getId(),
                        "TXN-001"
                );

        assertEquals(
                PaymentStatus.SUCCESS,
                response.status()
        );

        assertEquals("TXN-001", response.transactionId());

        Payment updatedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                updatedPayment.getStatus()
        );

        assertEquals(
                "TXN-001",
                updatedPayment.getTransactionId()
        );
    }

    @Test
    void shouldFailPendingPayment() {

        Payment payment = createPendingPayment();

        PaymentResponse response =
                paymentService.failPayment(
                        payment.getId(),
                        "TXN-002"
                );

        assertEquals(
                PaymentStatus.FAILED,
                response.status()
        );

        Payment updatedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.FAILED,
                updatedPayment.getStatus()
        );

        assertEquals(
                "TXN-002",
                updatedPayment.getTransactionId()
        );
    }

    @Test
    void shouldHandleDuplicateSuccessCallback() {

        Payment payment = createPendingPayment();

        PaymentResponse firstResponse =
                paymentService.confirmPayment(
                        payment.getId(),
                        "TXN-003"
                );

        PaymentResponse secondResponse =
                paymentService.confirmPayment(
                        payment.getId(),
                        "TXN-003"
                );

        assertEquals(firstResponse, secondResponse);

        assertEquals(
                PaymentStatus.SUCCESS,
                secondResponse.status()
        );

        assertEquals(1, paymentRepository.count());
    }

    @Test
    void shouldHandleDuplicateFailureCallback() {

        Payment payment = createPendingPayment();

        PaymentResponse firstResponse =
                paymentService.failPayment(
                        payment.getId(),
                        "TXN-004"
                );

        PaymentResponse secondResponse =
                paymentService.failPayment(
                        payment.getId(),
                        "TXN-004"
                );

        assertEquals(firstResponse, secondResponse);

        assertEquals(
                PaymentStatus.FAILED,
                secondResponse.status()
        );

        assertEquals(1, paymentRepository.count());
    }

    @Test
    void shouldRejectFailureAfterSuccess() {

        Payment payment = createPendingPayment();

        paymentService.confirmPayment(
                payment.getId(),
                "TXN-005"
        );

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.failPayment(
                        payment.getId(),
                        "TXN-005"
                )
        );

        Payment existingPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                existingPayment.getStatus()
        );
    }

    @Test
    void shouldRejectSuccessAfterFailure() {

        Payment payment = createPendingPayment();

        paymentService.failPayment(
                payment.getId(),
                "TXN-006"
        );

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.confirmPayment(
                        payment.getId(),
                        "TXN-006"
                )
        );

        Payment existingPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.FAILED,
                existingPayment.getStatus()
        );
    }

    @Test
    void shouldRejectDifferentTransactionId() {

        Payment payment = createPendingPayment();

        paymentService.confirmPayment(
                payment.getId(),
                "TXN-007"
        );

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.confirmPayment(
                        payment.getId(),
                        "TXN-008"
                )
        );

        Payment existingPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                "TXN-007",
                existingPayment.getTransactionId()
        );
    }

    @Test
    void shouldRejectNonExistentPayment() {

        assertThrows(
                EntityNotFoundException.class,
                () -> paymentService.confirmPayment(
                        Long.MAX_VALUE,
                        "TXN-009"
                )
        );
    }

    @Test
    void shouldRejectMissingTransactionId() {

        Payment payment = createPendingPayment();

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.confirmPayment(
                        payment.getId(),
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.confirmPayment(
                        payment.getId(),
                        " "
                )
        );

        Payment existingPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                existingPayment.getStatus()
        );
    }
}
