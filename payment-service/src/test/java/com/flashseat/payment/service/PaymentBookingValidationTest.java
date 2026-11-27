package com.flashseat.payment.service;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.BookingResponse;
import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.gateway.PaymentGateway;
import com.flashseat.payment.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentBookingValidationTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingClient bookingClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private PaymentGateway paymentGateway;

    private PaymentService paymentService;

    @Mock
    private OutboxEventService outboxEventService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentGateway = mock(PaymentGateway.class);
        paymentService = new PaymentService(
                bookingClient,
                paymentRepository,
                transactionTemplate,
                paymentGateway,
                outboxEventService
        );

        when(paymentRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.empty());
    }

    private BookingResponse booking(
            String status,
            BigDecimal amount,
            LocalDateTime expiresAt) {

        return new BookingResponse(
                101L,
                10L,
                1L,
                status,
                amount,
                LocalDateTime.now(),
                expiresAt
        );
    }

    private InitiatePaymentRequest request() {
        return new InitiatePaymentRequest(
                101L,
                new BigDecimal("500.00"),
                "test-key"
        );
    }

    @Test
    void cancelledBookingShouldBeRejected() {

        when(bookingClient.getBooking(101L))
                .thenReturn(booking(
                        "CANCELLED",
                        new BigDecimal("500.00"),
                        LocalDateTime.now().plusMinutes(5)
                ));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.initiatePayment(request())
        );

        verify(transactionTemplate, never())
                .execute(any());
    }

    @Test
    void confirmedBookingShouldBeRejected() {

        when(bookingClient.getBooking(101L))
                .thenReturn(booking(
                        "CONFIRMED",
                        new BigDecimal("500.00"),
                        LocalDateTime.now().plusMinutes(5)
                ));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.initiatePayment(request())
        );

        verify(transactionTemplate, never())
                .execute(any());
    }

    @Test
    void expiredBookingShouldBeRejected() {

        when(bookingClient.getBooking(101L))
                .thenReturn(booking(
                        "PENDING",
                        new BigDecimal("500.00"),
                        LocalDateTime.now().minusMinutes(1)
                ));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.initiatePayment(request())
        );

        verify(transactionTemplate, never())
                .execute(any());
    }

    @Test
    void mismatchedAmountShouldBeRejected() {

        when(bookingClient.getBooking(101L))
                .thenReturn(booking(
                        "PENDING",
                        new BigDecimal("700.00"),
                        LocalDateTime.now().plusMinutes(5)
                ));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.initiatePayment(request())
        );

        verify(transactionTemplate, never())
                .execute(any());
    }

    @Test
    void emptyBookingResponseShouldBeRejected() {

        when(bookingClient.getBooking(101L))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.initiatePayment(request())
        );

        verify(transactionTemplate, never())
                .execute(any());
    }
}
