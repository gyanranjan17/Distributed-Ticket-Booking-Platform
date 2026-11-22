
package com.flashseat.booking.repository;

import com.flashseat.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import com.flashseat.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    @Query("""
    SELECT b.id
    FROM Booking b
    WHERE b.status = :status
      AND b.expiresAt IS NOT NULL
      AND b.expiresAt <= :currentTime
    """)
    List<Long> findExpiredBookingIds(
            @Param("status") BookingStatus status,
            @Param("currentTime") LocalDateTime currentTime
    );

    @Modifying
    @Query("""
    UPDATE Booking b
    SET b.status = :newStatus
    WHERE b.id = :bookingId
      AND b.status = :expectedStatus
    """)
    int updateBookingStatusIfMatch(
            @Param("bookingId") Long bookingId,
            @Param("expectedStatus") BookingStatus expectedStatus,
            @Param("newStatus") BookingStatus newStatus
    );

    @Modifying
    @Query("""
    UPDATE Booking b
    SET b.status = :expiredStatus
    WHERE b.id = :bookingId
      AND b.status = :pendingStatus
      AND b.expiresAt IS NOT NULL
      AND b.expiresAt <= :currentTime
    """)
    int expireBookingIfPending(
            @Param("bookingId") Long bookingId,
            @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("expiredStatus") BookingStatus expiredStatus,
            @Param("currentTime") LocalDateTime currentTime
    );

    @Modifying
    @Query("""
    UPDATE Booking b
    SET b.status = :confirmedStatus
    WHERE b.id = :bookingId
      AND b.status = :pendingStatus
      AND b.expiresAt IS NOT NULL
      AND b.expiresAt > :currentTime
    """)
    int confirmBookingIfPendingAndNotExpired(
            @Param("bookingId") Long bookingId,
            @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("confirmedStatus") BookingStatus confirmedStatus,
            @Param("currentTime") LocalDateTime currentTime
    );


}
