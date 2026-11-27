package com.flashseat.payment.service;

import com.flashseat.payment.entity.OutboxEvent;
import com.flashseat.payment.entity.OutboxStatus;
import com.flashseat.payment.entity.Payment;
import com.flashseat.payment.entity.PaymentStatus;
import com.flashseat.payment.event.PaymentSucceededEvent;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentSucceeded(Payment payment) {

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Cannot create success event for an unsuccessful payment"
            );
        }

        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                eventId,
                "PaymentSucceeded",
                1,
                occurredAt,
                payment.getId(),
                payment.getBookingId(),
                payment.getAmount(),
                payment.getTransactionId()
        );

        String payload = objectMapper.writeValueAsString(event);

        OutboxEvent outboxEvent = new OutboxEvent();

        outboxEvent.setId(eventId);
        outboxEvent.setAggregateType("PAYMENT");
        outboxEvent.setAggregateId(payment.getId());
        outboxEvent.setEventType("PaymentSucceeded");
        outboxEvent.setTopic("payment-events");
        outboxEvent.setPartitionKey(
                payment.getBookingId().toString()
        );
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setRetryCount(0);
        outboxEvent.setCreatedAt(occurredAt);

        entityManager.persist(outboxEvent);
    }
}