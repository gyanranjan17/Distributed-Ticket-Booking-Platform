package com.flashseat.payment.gateway;

import java.math.BigDecimal;

public interface PaymentGateway {

    GatewayPaymentResult processPayment(
            Long paymentId,
            BigDecimal amount
    );
}
