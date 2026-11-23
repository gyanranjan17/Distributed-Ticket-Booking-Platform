package com.flashseat.payment.service;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.BookingResponse;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingClient bookingClient;
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

        validateBooking(request);

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

    private void validateBooking(InitiatePaymentRequest request) {

        BookingResponse booking =
                bookingClient.getBooking(request.bookingId());

        if (booking == null) {
            throw new IllegalStateException(
                    "Booking Service returned an empty response"
            );
        }

        if (!"PENDING".equals(booking.status())) {
            throw new IllegalStateException(
                    "Booking is not pending: " + booking.status()
            );
        }

        if (booking.expiresAt() == null ||
                !booking.expiresAt().isAfter(LocalDateTime.now())) {

            throw new IllegalStateException(
                    "Booking has expired"
            );
        }

        if (booking.amount() == null ||
                booking.amount().compareTo(request.amount()) != 0) {

            throw new IllegalStateException(
                    "Payment amount does not match booking amount"
            );
        }
    }

}
