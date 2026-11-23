package com.flashseat.payment.service;

import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentResponse initiatePayment(InitiatePaymentRequest request) {

        // First, check whether this request was already processed.
        Payment existingPayment = paymentRepository
                .findByIdempotencyKey(request.idempotencyKey())
                .orElse(null);

        if (existingPayment != null) {
            validateIdempotentRequest(existingPayment, request);
            return mapToResponse(existingPayment);
        }

        try {
            Payment savedPayment = transactionTemplate.execute(status -> {

                Payment payment = new Payment();
                payment.setBookingId(request.bookingId());
                payment.setAmount(request.amount());
                payment.setIdempotencyKey(request.idempotencyKey());
                payment.setStatus(PaymentStatus.PENDING);

                return paymentRepository.saveAndFlush(payment);
            });

            return mapToResponse(savedPayment);

        } catch (DataIntegrityViolationException ex) {

            // The insert transaction has already rolled back.
            // A competing request may have inserted the same key.
            Payment concurrentPayment = paymentRepository
                    .findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> ex);

            validateIdempotentRequest(concurrentPayment, request);

            return mapToResponse(concurrentPayment);
        }
    }

    private void validateIdempotentRequest(
            Payment existingPayment,
            InitiatePaymentRequest request
    ) {
        boolean sameBooking = existingPayment.getBookingId()
                .equals(request.bookingId());

        boolean sameAmount = existingPayment.getAmount()
                .compareTo(request.amount()) == 0;

        if (!sameBooking || !sameAmount) {
            throw new IllegalStateException(
                    "Idempotency key already used with different payment details"
            );
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
