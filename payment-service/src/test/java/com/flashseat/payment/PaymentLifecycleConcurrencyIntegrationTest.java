package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.PaymentCallbackRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.gateway.GatewayPaymentStatus;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.service.PaymentService;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PaymentLifecycleConcurrencyIntegrationTest {

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

    private record CallbackResult(
            PaymentResponse response,
            boolean rejected
    ) {
    }

    private CallbackResult executeCallback(
            PaymentCallbackRequest request
    ) {

        try {

            return new CallbackResult(
                    paymentService.handlePaymentCallback(request),
                    false
            );

        } catch (IllegalStateException ex) {

            return new CallbackResult(null, true);
        }
    }

    private <T> List<T> runConcurrently(
            int threadCount,
            IntFunction<T> task
    ) throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<T>> futures = new ArrayList<>();

        try {

            for (int i = 0; i < threadCount; i++) {

                final int index = i;

                futures.add(executor.submit(() -> {

                    readyLatch.countDown();

                    if (!startLatch.await(
                            10, TimeUnit.SECONDS
                    )) {
                        throw new IllegalStateException(
                                "Start latch timed out"
                        );
                    }

                    return task.apply(index);
                }));
            }

            assertTrue(
                    readyLatch.await(10, TimeUnit.SECONDS),
                    "Threads were not ready in time"
            );

            startLatch.countDown();

            List<T> results = new ArrayList<>();

            for (Future<T> future : futures) {

                results.add(
                        future.get(30, TimeUnit.SECONDS)
                );
            }

            return results;

        } finally {

            startLatch.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(
                            10, TimeUnit.SECONDS
                    ),
                    "Executor did not terminate"
            );
        }
    }

    @Test
    void shouldHandleIdenticalConcurrentCallbacks()
            throws Exception {

        Payment payment = createPendingPayment();

        PaymentCallbackRequest request =
                new PaymentCallbackRequest(
                        payment.getId(),
                        "TXN-CONCURRENT-001",
                        GatewayPaymentStatus.SUCCESS
                );

        List<PaymentResponse> responses =
                runConcurrently(
                        10,
                        index -> paymentService
                                .handlePaymentCallback(request)
                );

        assertEquals(10, responses.size());

        PaymentResponse firstResponse = responses.get(0);

        for (PaymentResponse response : responses) {

            assertEquals(firstResponse, response);

            assertEquals(
                    PaymentStatus.SUCCESS,
                    response.status()
            );

            assertEquals(
                    "TXN-CONCURRENT-001",
                    response.transactionId()
            );
        }

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                savedPayment.getStatus()
        );

        assertEquals(
                "TXN-CONCURRENT-001",
                savedPayment.getTransactionId()
        );

        assertEquals(1L, paymentRepository.count());
    }

    @Test
    void shouldRejectConcurrentConflictingTransactionIds()
            throws Exception {

        Payment payment = createPendingPayment();

        List<CallbackResult> results =
                runConcurrently(10, index -> {

                    PaymentCallbackRequest request =
                            new PaymentCallbackRequest(
                                    payment.getId(),
                                    "TXN-CONFLICT-" + index,
                                    GatewayPaymentStatus.SUCCESS
                            );

                    return executeCallback(request);
                });

        long successful = results.stream()
                .filter(result -> !result.rejected())
                .count();

        long rejected = results.stream()
                .filter(CallbackResult::rejected)
                .count();

        assertEquals(1L, successful);
        assertEquals(9L, rejected);

        PaymentResponse winningResponse = results.stream()
                .filter(result -> !result.rejected())
                .findFirst()
                .orElseThrow()
                .response();

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                savedPayment.getStatus()
        );

        assertEquals(
                winningResponse.transactionId(),
                savedPayment.getTransactionId()
        );

        assertEquals(1L, paymentRepository.count());
    }

    @Test
    void shouldAllowOnlyOneTerminalState()
            throws Exception {

        Payment payment = createPendingPayment();

        List<CallbackResult> results =
                runConcurrently(2, index -> {

                    GatewayPaymentStatus gatewayStatus =
                            index == 0
                                    ? GatewayPaymentStatus.SUCCESS
                                    : GatewayPaymentStatus.FAILED;

                    PaymentCallbackRequest request =
                            new PaymentCallbackRequest(
                                    payment.getId(),
                                    "TXN-RACE-001",
                                    gatewayStatus
                            );

                    return executeCallback(request);
                });

        long successful = results.stream()
                .filter(result -> !result.rejected())
                .count();

        long rejected = results.stream()
                .filter(CallbackResult::rejected)
                .count();

        assertEquals(1L, successful);
        assertEquals(1L, rejected);

        PaymentResponse winningResponse = results.stream()
                .filter(result -> !result.rejected())
                .findFirst()
                .orElseThrow()
                .response();

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertTrue(savedPayment.getStatus().isTerminal());

        assertEquals(
                winningResponse.status(),
                savedPayment.getStatus()
        );

        assertEquals(
                winningResponse.transactionId(),
                savedPayment.getTransactionId()
        );

        assertEquals(1L, paymentRepository.count());
    }

    @Test
    void shouldHandleConcurrentPaymentProcessing()
            throws Exception {

        Payment payment = createPendingPayment();

        List<PaymentResponse> responses =
                runConcurrently(
                        10,
                        index -> paymentService
                                .processPayment(payment.getId())
                );

        Set<String> transactionIds = new HashSet<>();

        assertEquals(10, responses.size());

        for (PaymentResponse response : responses) {

            assertEquals(
                    PaymentStatus.SUCCESS,
                    response.status()
            );

            assertNotNull(response.transactionId());

            transactionIds.add(
                    response.transactionId()
            );
        }

        assertEquals(1, transactionIds.size());

        Payment savedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCESS,
                savedPayment.getStatus()
        );

        assertEquals(
                transactionIds.iterator().next(),
                savedPayment.getTransactionId()
        );

        assertEquals(1L, paymentRepository.count());
    }
}
