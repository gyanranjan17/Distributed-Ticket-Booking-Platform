package com.flashseat.payment.controller;

import com.flashseat.payment.dto.PaymentCallbackRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.service.PaymentService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Profile({"dev", "test"})
public class PaymentCallbackController {

    private final PaymentService paymentService;

    @PostMapping("/callback")
    public ResponseEntity<PaymentResponse> handleCallback(
            @Valid @RequestBody PaymentCallbackRequest request
    ) {

        PaymentResponse response =
                paymentService.handlePaymentCallback(request);

        return ResponseEntity.ok(response);
    }
}
