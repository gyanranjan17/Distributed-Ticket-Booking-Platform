package com.flashseat.payment;

import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import java.util.List;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.BookingResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class PaymentIdempotencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @MockitoBean
    private BookingClient bookingClient;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void mockBookingService() {
        paymentRepository.deleteAllInBatch();
        when(bookingClient.getBooking(anyLong()))
                .thenAnswer(invocation -> {

                    Long bookingId = invocation.getArgument(0);

                    return new BookingResponse(
                            bookingId,
                            10L,
                            1L,
                            "PENDING",
                            new BigDecimal("500.00"),
                            LocalDateTime.now(),
                            LocalDateTime.now().plusMinutes(10)
                    );
                });
    }

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

    @Test
    void validBookingShouldCreatePayment() {

        String key = UUID.randomUUID().toString();

        InitiatePaymentRequest request =
                new InitiatePaymentRequest(
                        101L,
                        new BigDecimal("500.00"),
                        key
                );

        PaymentResponse response =
                paymentService.initiatePayment(request);

        assertNotNull(response);
        assertNotNull(response.paymentId());
        assertEquals("PENDING", response.status().toString());

        verify(bookingClient, times(1))
                .getBooking(101L);
    }

    @Test
    void idempotentRetryShouldNotCallBookingServiceAgain() {
        paymentRepository.deleteAllInBatch();
        String key = UUID.randomUUID().toString();

        InitiatePaymentRequest request =
                new InitiatePaymentRequest(
                        101L,
                        new BigDecimal("500.00"),
                        key
                );

        PaymentResponse firstResponse =
                paymentService.initiatePayment(request);

        PaymentResponse secondResponse =
                paymentService.initiatePayment(request);

        assertEquals(
                firstResponse.paymentId(),
                secondResponse.paymentId()
        );

        verify(bookingClient, times(1))
                .getBooking(101L);
    }

    @Test
    void shouldAllowNewPaymentAfterPreviousPaymentFailed() {

        Long bookingId = 101L;
        BigDecimal amount = new BigDecimal("500.00");

        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();

        // Create the first payment
        PaymentResponse firstResponse =
                paymentService.initiatePayment(
                        new InitiatePaymentRequest(
                                bookingId,
                                amount,
                                firstKey
                        )
                );

        // Simulate a confirmed payment failure
        Payment firstPayment = paymentRepository
                .findById(firstResponse.paymentId())
                .orElseThrow();

        firstPayment.setStatus(PaymentStatus.FAILED);

        paymentRepository.saveAndFlush(firstPayment);

        // Initiate a new payment for the same booking
        PaymentResponse secondResponse =
                paymentService.initiatePayment(
                        new InitiatePaymentRequest(
                                bookingId,
                                amount,
                                secondKey
                        )
                );

        // Verify that a new payment was created
        assertNotEquals(
                firstResponse.paymentId(),
                secondResponse.paymentId()
        );

        assertEquals(
                PaymentStatus.PENDING,
                secondResponse.status()
        );

        // Verify both payment records exist
        List<Payment> payments =
                paymentRepository.findByBookingId(bookingId);

        assertEquals(2, payments.size());

        long failedCount = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .count();

        long pendingCount = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .count();

        assertEquals(1, failedCount);
        assertEquals(1, pendingCount);
    }

}
