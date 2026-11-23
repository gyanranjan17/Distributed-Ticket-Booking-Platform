package com.flashseat.payment.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record InitiatePaymentRequest(

        @NotNull
        @Positive
        Long bookingId,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @Size(max = 100)
        @NotBlank
        String idempotencyKey

) {}
