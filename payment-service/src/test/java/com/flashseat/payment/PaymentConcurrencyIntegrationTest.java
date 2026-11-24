package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import com.flashseat.payment.dto.BookingResponse;
import java.util.concurrent.atomic.AtomicInteger;
import com.flashseat.payment.entity.Payment;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PaymentConcurrencyIntegrationTest {

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
    void concurrentRequestsWithSameKeyShouldCreateOnlyOnePayment()
            throws Exception {

        String idempotencyKey = UUID.randomUUID().toString();

        InitiatePaymentRequest request = new InitiatePaymentRequest(
                101L,
                new BigDecimal("500.00"),
                idempotencyKey
        );

        int requestCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startSignal = new CountDownLatch(1);

        List<Future<PaymentResponse>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {

                futures.add(executor.submit(() -> {
                    startSignal.await();
                    return paymentService.initiatePayment(request);
                }));
            }

            // Release all worker threads at approximately the same time.
            startSignal.countDown();

            List<PaymentResponse> responses = new ArrayList<>();

            for (Future<PaymentResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            Long expectedPaymentId = responses.get(0).paymentId();

            for (PaymentResponse response : responses) {
                assertEquals(expectedPaymentId, response.paymentId());
                assertEquals(PaymentStatus.PENDING, response.status());
            }

            List<com.flashseat.payment.entity.Payment> payments =
                    paymentRepository.findByBookingId(101L);

            long matchingPayments = payments.stream()
                    .filter(p -> idempotencyKey.equals(p.getIdempotencyKey()))
                    .count();

            assertEquals(1, matchingPayments);

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRequestsWithDifferentKeysShouldCreateOnlyOnePayment()
            throws Exception {

        Long bookingId = 101L;
        int requestCount = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch startSignal = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {

                InitiatePaymentRequest request =
                        new InitiatePaymentRequest(
                                bookingId,
                                new BigDecimal("500.00"),
                                UUID.randomUUID().toString()
                        );

                futures.add(executor.submit(() -> {

                    startSignal.await();

                    try {
                        paymentService.initiatePayment(request);
                        successCount.incrementAndGet();

                    } catch (IllegalStateException ex) {

                        assertEquals(
                                "An active payment already exists for this booking",
                                ex.getMessage()
                        );

                        conflictCount.incrementAndGet();
                    }

                    return null;
                }));
            }

            startSignal.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            assertEquals(1, successCount.get());
            assertEquals(9, conflictCount.get());

            List<Payment> payments =
                    paymentRepository.findByBookingId(bookingId);

            assertEquals(1, payments.size());

            assertEquals(
                    PaymentStatus.PENDING,
                    payments.get(0).getStatus()
            );

        } finally {
            executor.shutdownNow();
        }
    }

}
