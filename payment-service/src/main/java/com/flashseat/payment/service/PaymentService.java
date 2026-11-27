package com.flashseat.payment.service;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.InitiatePaymentRequest;
import com.flashseat.payment.dto.PaymentResponse;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.repository.PaymentRepository;
import com.flashseat.payment.dto.BookingResponse;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import com.flashseat.payment.dto.PaymentCallbackRequest;
import java.util.Objects;
import jakarta.persistence.EntityNotFoundException;
import com.flashseat.payment.gateway.PaymentGateway;
import com.flashseat.payment.gateway.GatewayPaymentResult;

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
    private final PaymentGateway paymentGateway;
    private final OutboxEventService outboxEventService;

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

        }catch (DataIntegrityViolationException ex) {

            boolean duplicateIdempotencyKey =
                    isUniqueConstraintViolation(
                            ex,
                            "payments_idempotency_key_key"
                    );

            boolean duplicateActivePayment =
                    isUniqueConstraintViolation(
                            ex,
                            "uk_active_payment_per_booking"
                    );

            if (!duplicateIdempotencyKey && !duplicateActivePayment) {
                throw ex;
            }

            // Check whether a concurrent request used the same key.
            Payment concurrentPayment = paymentRepository
                    .findByIdempotencyKey(request.idempotencyKey())
                    .orElse(null);

            if (concurrentPayment != null) {

                validateIdempotentRequest(concurrentPayment, request);

                return mapToResponse(concurrentPayment);
            }

            if (duplicateActivePayment) {
                throw new IllegalStateException(
                        "An active payment already exists for this booking"
                );
            }

            throw ex;
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

    private boolean isUniqueConstraintViolation(
            Throwable exception,
            String constraintName
    ) {
        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof ConstraintViolationException violation) {

                if ("23505".equals(violation.getSQLState())
                        && constraintName.equals(
                        violation.getConstraintName())) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }

    public PaymentResponse confirmPayment(
            Long paymentId,
            String transactionId
    ) {
        return transitionPaymentStatus(
                paymentId,
                PaymentStatus.SUCCESS,
                transactionId
        );
    }

    public PaymentResponse failPayment(
            Long paymentId,
            String transactionId
    ) {
        return transitionPaymentStatus(
                paymentId,
                PaymentStatus.FAILED,
                transactionId
        );
    }

    private PaymentResponse transitionPaymentStatus(
            Long paymentId,
            PaymentStatus targetStatus,
            String transactionId
    ) {

        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Transaction ID is required"
            );
        }

        return transactionTemplate.execute(status -> {

            int updatedRows =
                    paymentRepository.updatePaymentStatusIfCurrent(
                            paymentId,
                            PaymentStatus.PENDING,
                            targetStatus,
                            transactionId,
                            LocalDateTime.now()
                    );

            Payment payment = paymentRepository
                    .findById(paymentId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Payment not found: " + paymentId
                    ));

            // Successful state transition
            if (updatedRows == 1) {
                if (targetStatus == PaymentStatus.SUCCESS) {
                    outboxEventService.recordPaymentSucceeded(payment);
                }

                return mapToResponse(payment);
            }

            // Idempotent replay
            if (payment.getStatus() == targetStatus
                    && Objects.equals(
                    payment.getTransactionId(),
                    transactionId
            )) {

                return mapToResponse(payment);
            }

            // Conflicting callback
            throw new IllegalStateException(
                    "Cannot transition payment from "
                            + payment.getStatus()
                            + " to "
                            + targetStatus
                            + " with the supplied transaction ID"
            );
        });
    }

    public PaymentResponse processPayment(Long paymentId) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found: " + paymentId
                ));

        // Avoid processing an already completed payment
        if (payment.getStatus().isTerminal()) {
            return mapToResponse(payment);
        }

        // Call the external payment gateway
        GatewayPaymentResult result =
                paymentGateway.processPayment(
                        payment.getId(),
                        payment.getAmount()
                );

        // Verify the gateway result belongs to this payment
        if (!Objects.equals(
                payment.getId(),
                result.paymentId()
        )) {
            throw new IllegalStateException(
                    "Gateway returned a mismatched payment ID"
            );
        }

        // Apply the gateway outcome
        return switch (result.status()) {

            case SUCCESS -> confirmPayment(
                    paymentId,
                    result.transactionId()
            );

            case FAILED -> failPayment(
                    paymentId,
                    result.transactionId()
            );

            case UNKNOWN -> paymentRepository
                    .findById(paymentId)
                    .map(this::mapToResponse)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Payment not found: " + paymentId
                    ));
        };
    }

    public PaymentResponse handlePaymentCallback(
            PaymentCallbackRequest request
    ) {

        return switch (request.status()) {

            case SUCCESS -> confirmPayment(
                    request.paymentId(),
                    request.transactionId()
            );

            case FAILED -> failPayment(
                    request.paymentId(),
                    request.transactionId()
            );

            case UNKNOWN -> throw new IllegalArgumentException(
                    "Callback must contain a definitive payment status"
            );
        };
    }

}
