
package com.flashseat.payment.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SimulatedPaymentGateway
        implements PaymentGateway {

    private final ConcurrentMap<Long, ProcessedPayment>
            processedPayments = new ConcurrentHashMap<>();

    private record ProcessedPayment(
            BigDecimal amount,
            GatewayPaymentResult result
    ) {}

    @Override
    public GatewayPaymentResult processPayment(
            Long paymentId,
            BigDecimal amount
    ) {

        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid payment ID"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive"
            );
        }

        ProcessedPayment processedPayment =
                processedPayments.computeIfAbsent(
                        paymentId,
                        id -> new ProcessedPayment(
                                amount,
                                new GatewayPaymentResult(
                                        id,
                                        "TXN-" + UUID.randomUUID(),
                                        GatewayPaymentStatus.SUCCESS
                                )
                        )
                );

        if (processedPayment.amount()
                .compareTo(amount) != 0) {
            throw new IllegalStateException(
                    "Payment already processed with a different amount"
            );
        }

        return processedPayment.result();
    }
}
