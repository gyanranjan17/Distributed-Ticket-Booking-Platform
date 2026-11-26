package com.flashseat.payment.repository;

import com.flashseat.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flashseat.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    UPDATE Payment p
    SET p.status = :newStatus,
        p.transactionId = :transactionId,
        p.updatedAt = :updatedAt
    WHERE p.id = :paymentId
    AND p.status = :expectedStatus
    """)
    int updatePaymentStatusIfCurrent(
            @Param("paymentId") Long paymentId,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("newStatus") PaymentStatus newStatus,
            @Param("transactionId") String transactionId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

}
