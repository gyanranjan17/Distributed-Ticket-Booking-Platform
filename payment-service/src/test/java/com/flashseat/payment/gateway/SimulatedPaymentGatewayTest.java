package com.flashseat.payment.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedPaymentGatewayTest {

    private SimulatedPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SimulatedPaymentGateway();
    }

    @Test
    void shouldProcessValidPayment() {

        GatewayPaymentResult result =
                gateway.processPayment(
                        101L,
                        new BigDecimal("500.00")
                );

        assertEquals(101L, result.paymentId());

        assertEquals(
                GatewayPaymentStatus.SUCCESS,
                result.status()
        );

        assertNotNull(result.transactionId());

        assertTrue(
                result.transactionId().startsWith("TXN-")
        );
    }

    @Test
    void shouldReturnSameResultForDuplicateRequest() {

        GatewayPaymentResult first =
                gateway.processPayment(
                        101L,
                        new BigDecimal("500.00")
                );

        GatewayPaymentResult second =
                gateway.processPayment(
                        101L,
                        new BigDecimal("500.00")
                );

        assertEquals(first, second);
    }

    @Test
    void shouldRejectSamePaymentWithDifferentAmount() {

        gateway.processPayment(
                101L,
                new BigDecimal("500.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> gateway.processPayment(
                        101L,
                        new BigDecimal("700.00")
                )
        );
    }

    @Test
    void shouldAcceptSameAmountWithDifferentScale() {

        GatewayPaymentResult first =
                gateway.processPayment(
                        101L,
                        new BigDecimal("500.0")
                );

        GatewayPaymentResult second =
                gateway.processPayment(
                        101L,
                        new BigDecimal("500.00")
                );

        assertEquals(first, second);
    }

    @Test
    void shouldCreateDifferentTransactionsForDifferentPayments() {

        GatewayPaymentResult first =
                gateway.processPayment(
                        101L,
                        new BigDecimal("500.00")
                );

        GatewayPaymentResult second =
                gateway.processPayment(
                        102L,
                        new BigDecimal("500.00")
                );

        assertNotEquals(
                first.transactionId(),
                second.transactionId()
        );
    }

    @Test
    void shouldRejectInvalidPaymentId() {

        BigDecimal amount = new BigDecimal("500.00");

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.processPayment(null, amount)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.processPayment(0L, amount)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.processPayment(-1L, amount)
        );
    }

    @Test
    void shouldRejectInvalidAmount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.processPayment(101L, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.processPayment(
                        101L,
                        BigDecimal.ZERO
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.processPayment(
                        101L,
                        new BigDecimal("-500.00")
                )
        );
    }

    @Test
    void shouldReturnSameTransactionForConcurrentRequests()
            throws Exception {

        int requestCount = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch readySignal =
                new CountDownLatch(requestCount);

        CountDownLatch startSignal =
                new CountDownLatch(1);

        List<Future<GatewayPaymentResult>> futures =
                new ArrayList<>();

        try {

            for (int i = 0; i < requestCount; i++) {

                futures.add(executor.submit(() -> {

                    readySignal.countDown();

                    startSignal.await();

                    return gateway.processPayment(
                            101L,
                            new BigDecimal("500.00")
                    );
                }));
            }

            assertTrue(
                    readySignal.await(10, TimeUnit.SECONDS),
                    "Not all worker threads became ready"
            );

            startSignal.countDown();

            Set<String> transactionIds = new HashSet<>();

            for (Future<GatewayPaymentResult> future : futures) {

                GatewayPaymentResult result =
                        future.get(10, TimeUnit.SECONDS);

                assertEquals(101L, result.paymentId());

                assertEquals(
                        GatewayPaymentStatus.SUCCESS,
                        result.status()
                );

                transactionIds.add(result.transactionId());
            }

            assertEquals(1, transactionIds.size());

        } finally {
            executor.shutdownNow();
        }
    }
}
