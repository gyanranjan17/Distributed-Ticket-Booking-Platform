package com.flashseat.payment.gateway;

public record GatewayPaymentResult(

        Long paymentId,
        String transactionId,
        GatewayPaymentStatus status

) {
}
