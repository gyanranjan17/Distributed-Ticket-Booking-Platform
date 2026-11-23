package com.flashseat.payment.dto;

import com.flashseat.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(

        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        PaymentStatus status,
        String transactionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {}
