package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.gateway.PaymentGateway;
import com.flashseat.payment.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

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
@ActiveProfiles("test")
class PaymentCallbackControllerIntegrationTest {

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

    private String callbackBody(
            Long paymentId,
            String transactionId,
            String status
    ) {

        return """
                {
                    "paymentId": %d,
                    "transactionId": "%s",
                    "status": "%s"
                }
                """.formatted(
                paymentId,
                transactionId,
                status
        );
    }

    @Test
    void shouldHandleSuccessfulCallback() throws Exception {

        Payment payment = createPendingPayment();

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "TXN-001",
                                        "SUCCESS"
                                ))
                )
                .andExpect(status().isOk())
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

        assertEquals(
                "TXN-001",
                savedPayment.getTransactionId()
        );

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldHandleFailedCallback() throws Exception {

        Payment payment = createPendingPayment();

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "TXN-002",
                                        "FAILED"
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("FAILED"));

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

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldHandleDuplicateCallback() throws Exception {

        Payment payment = createPendingPayment();

        String requestBody = callbackBody(
                payment.getId(),
                "TXN-003",
                "SUCCESS"
        );

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("SUCCESS"));

        Payment firstPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
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

        assertEquals(1L, paymentRepository.count());

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void shouldRejectConflictingCallback() throws Exception {

        Payment payment = createPendingPayment();

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "TXN-004",
                                        "SUCCESS"
                                ))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "TXN-004",
                                        "FAILED"
                                ))
                )
                .andExpect(status().isConflict());

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                savedPayment.getStatus()
        );

        assertEquals(
                "TXN-004",
                savedPayment.getTransactionId()
        );
    }

    @Test
    void shouldRejectDifferentTransactionId() throws Exception {

        Payment payment = createPendingPayment();

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "TXN-005",
                                        "SUCCESS"
                                ))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "TXN-006",
                                        "SUCCESS"
                                ))
                )
                .andExpect(status().isConflict());

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                "TXN-005",
                savedPayment.getTransactionId()
        );
    }

    @Test
    void shouldRejectUnknownCallbackStatus() throws Exception {

        Payment payment = createPendingPayment();

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "TXN-007",
                                        "UNKNOWN"
                                ))
                )
                .andExpect(status().isBadRequest());

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
    void shouldRejectMissingTransactionId() throws Exception {

        Payment payment = createPendingPayment();

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        payment.getId(),
                                        "",
                                        "SUCCESS"
                                ))
                )
                .andExpect(status().isBadRequest());

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                savedPayment.getStatus()
        );
    }

    @Test
    void shouldReturnNotFoundForMissingPayment()
            throws Exception {

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(callbackBody(
                                        Long.MAX_VALUE,
                                        "TXN-008",
                                        "SUCCESS"
                                ))
                )
                .andExpect(status().isNotFound());
    }
}
