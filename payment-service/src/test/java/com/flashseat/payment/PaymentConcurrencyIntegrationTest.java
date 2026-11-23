package com.flashseat.payment;

import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

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
}
