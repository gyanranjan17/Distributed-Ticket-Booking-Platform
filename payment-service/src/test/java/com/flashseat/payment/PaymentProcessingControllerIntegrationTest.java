package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.gateway.GatewayPaymentResult;
import com.flashseat.payment.gateway.GatewayPaymentStatus;
import com.flashseat.payment.gateway.PaymentGateway;
import com.flashseat.payment.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentProcessingControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

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
    void shouldProcessSuccessfulPayment() throws Exception {

        Payment payment = createPendingPayment();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId(),
                "TXN-001",
                GatewayPaymentStatus.SUCCESS
        ));

        mockMvc.perform(
                        post("/api/payments/{paymentId}/process",
                                payment.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(payment.getId()))
                .andExpect(jsonPath("$.status")
                        .value("SUCCESS"))
                .andExpect(jsonPath("$.transactionId")
                        .value("TXN-001"));

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                savedPayment.getStatus()
        );
    }

    @Test
    void shouldProcessFailedPayment() throws Exception {

        Payment payment = createPendingPayment();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId(),
                "TXN-002",
                GatewayPaymentStatus.FAILED
        ));

        mockMvc.perform(
                        post("/api/payments/{paymentId}/process",
                                payment.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("FAILED"))
                .andExpect(jsonPath("$.transactionId")
                        .value("TXN-002"));

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.FAILED,
                savedPayment.getStatus()
        );
    }

    @Test
    void shouldKeepUnknownPaymentPending() throws Exception {

        Payment payment = createPendingPayment();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId(),
                null,
                GatewayPaymentStatus.UNKNOWN
        ));

        mockMvc.perform(
                        post("/api/payments/{paymentId}/process",
                                payment.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("PENDING"));

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
    void shouldReturnNotFoundForMissingPayment()
            throws Exception {

        mockMvc.perform(
                        post("/api/payments/{paymentId}/process",
                                Long.MAX_VALUE)
                )
                .andExpect(status().isNotFound());

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldHandleDuplicateProcessingRequest()
            throws Exception {

        Payment payment = createPendingPayment();

        when(paymentGateway.processPayment(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(new GatewayPaymentResult(
                payment.getId(),
                "TXN-003",
                GatewayPaymentStatus.SUCCESS
        ));

        mockMvc.perform(
                        post("/api/payments/{paymentId}/process",
                                payment.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("SUCCESS"))
                .andExpect(jsonPath("$.transactionId")
                        .value("TXN-003"));

        Payment firstPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        mockMvc.perform(
                        post("/api/payments/{paymentId}/process",
                                payment.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("SUCCESS"))
                .andExpect(jsonPath("$.transactionId")
                        .value("TXN-003"));

        Payment secondPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                firstPayment.getUpdatedAt(),
                secondPayment.getUpdatedAt()
        );

        verify(paymentGateway, times(1))
                .processPayment(
                        payment.getId(),
                        payment.getAmount()
                );

        assertEquals(1L, paymentRepository.count());
    }
}
