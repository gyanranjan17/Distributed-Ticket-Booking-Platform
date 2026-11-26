package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.gateway.GatewayPaymentResult;
import com.flashseat.payment.gateway.GatewayPaymentStatus;
import com.flashseat.payment.gateway.PaymentGateway;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;

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
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class PaymentProcessingIntegrationTest {

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

    @MockitoBean
    private PaymentGateway paymentGateway;

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
    void shouldProcessSuccessfulPayment() {

        Payment payment = createPendingPayment();

        payment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId(),
                "TXN-001",
                GatewayPaymentStatus.SUCCESS
        ));

        PaymentResponse response =
                paymentService.processPayment(payment.getId());

        assertEquals(
                PaymentStatus.SUCCESS,
                response.status()
        );

        assertEquals("TXN-001", response.transactionId());

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                savedPayment.getStatus()
        );

        assertEquals(
                "TXN-001",
                savedPayment.getTransactionId()
        );

        verify(paymentGateway, times(1))
                .processPayment(
                        payment.getId(),
                        payment.getAmount()
                );
    }

    @Test
    void shouldProcessFailedPayment() {

        Payment payment = createPendingPayment();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId(),
                "TXN-002",
                GatewayPaymentStatus.FAILED
        ));

        PaymentResponse response =
                paymentService.processPayment(payment.getId());

        assertEquals(
                PaymentStatus.FAILED,
                response.status()
        );

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.FAILED,
                savedPayment.getStatus()
        );

        assertEquals(
                "TXN-002",
                savedPayment.getTransactionId()
        );
    }

    @Test
    void shouldKeepPaymentPendingForUnknownOutcome() {

        Payment createdpayment = createPendingPayment();

        Payment payment = paymentRepository
                .findById(createdpayment.getId())
                .orElseThrow();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId(),
                null,
                GatewayPaymentStatus.UNKNOWN
        ));

        PaymentResponse response =
                paymentService.processPayment(payment.getId());

        assertEquals(
                PaymentStatus.PENDING,
                response.status()
        );

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                savedPayment.getStatus()
        );

        assertNull(savedPayment.getTransactionId());

        assertEquals(
                payment.getUpdatedAt(),
                savedPayment.getUpdatedAt()
        );
    }

    @Test
    void shouldNotProcessAlreadySuccessfulPayment() {

        Payment payment = createPendingPayment();

        PaymentResponse confirmedPayment =
                paymentService.confirmPayment(
                        payment.getId(),
                        "TXN-003"
                );

        PaymentResponse response =
                paymentService.processPayment(payment.getId());

        assertEquals(confirmedPayment, response);

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldNotProcessAlreadyFailedPayment() {

        Payment payment = createPendingPayment();

        PaymentResponse failedPayment =
                paymentService.failPayment(
                        payment.getId(),
                        "TXN-004"
                );

        PaymentResponse response =
                paymentService.processPayment(payment.getId());

        assertEquals(failedPayment, response);

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldRejectMismatchedGatewayPaymentId() {

        Payment payment = createPendingPayment();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId() + 1,
                "TXN-005",
                GatewayPaymentStatus.SUCCESS
        ));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.processPayment(
                        payment.getId()
                )
        );

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                savedPayment.getStatus()
        );

        assertNull(savedPayment.getTransactionId());
    }

    @Test
    void shouldRejectNonExistentPayment() {

        assertThrows(
                EntityNotFoundException.class,
                () -> paymentService.processPayment(
                        Long.MAX_VALUE
                )
        );

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldKeepPaymentPendingWhenGatewayThrowsException() {

        Payment payment = createPendingPayment();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenThrow(
                new RuntimeException("Gateway unavailable")
        );

        assertThrows(
                RuntimeException.class,
                () -> paymentService.processPayment(
                        payment.getId()
                )
        );

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                savedPayment.getStatus()
        );

        assertNull(savedPayment.getTransactionId());
    }
}
