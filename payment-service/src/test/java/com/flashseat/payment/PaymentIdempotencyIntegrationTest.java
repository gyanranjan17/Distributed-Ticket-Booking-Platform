package com.flashseat.payment;

import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PaymentIdempotencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void sameRequestShouldReturnExistingPayment() {

        String key = UUID.randomUUID().toString();

        InitiatePaymentRequest request = new InitiatePaymentRequest(
                101L,
                new BigDecimal("500.00"),
                key
        );

        PaymentResponse first = paymentService.initiatePayment(request);
        PaymentResponse second = paymentService.initiatePayment(request);

        assertEquals(first.paymentId(), second.paymentId());

        assertEquals(
                1,
                paymentRepository.findByIdempotencyKey(key).stream().count()
        );
    }

    @Test
    void sameKeyWithDifferentBookingShouldFail() {

        String key = UUID.randomUUID().toString();

        paymentService.initiatePayment(
                new InitiatePaymentRequest(
                        101L,
                        new BigDecimal("500.00"),
                        key
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.initiatePayment(
                        new InitiatePaymentRequest(
                                102L,
                                new BigDecimal("500.00"),
                                key
                        )
                )
        );
    }

    @Test
    void sameKeyWithDifferentAmountShouldFail() {

        String key = UUID.randomUUID().toString();

        paymentService.initiatePayment(
                new InitiatePaymentRequest(
                        101L,
                        new BigDecimal("500.00"),
                        key
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.initiatePayment(
                        new InitiatePaymentRequest(
                                101L,
                                new BigDecimal("700.00"),
                                key
                        )
                )
        );
    }
}
