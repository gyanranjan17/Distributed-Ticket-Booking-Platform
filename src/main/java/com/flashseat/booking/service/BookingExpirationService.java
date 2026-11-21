package com.flashseat.booking.service;

import com.flashseat.booking.entity.*;
import com.flashseat.booking.exception.ResourceNotFoundException;
import com.flashseat.booking.repository.BookingRepository;
import com.flashseat.booking.repository.ShowSeatRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingExpirationService {

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public boolean expireBooking(Long bookingId) {

        // 1. Read booking to identify its seat
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + bookingId
                ));

        // 2. Atomically expire only an eligible booking
        int updatedRows = bookingRepository.expireBookingIfPending(
                bookingId,
                BookingStatus.PENDING,
                BookingStatus.EXPIRED,
                LocalDateTime.now()
        );

        if (updatedRows == 0) {
            return false;
        }

        // 3. Release the associated seat
        int releasedRows = showSeatRepository.updateSeatStatusIfMatch(
                booking.getShowSeat().getId(),
                SeatStatus.LOCKED,
                SeatStatus.AVAILABLE
        );

        if (releasedRows != 1) {
            throw new IllegalStateException(
                    "Failed to release seat for booking: " + bookingId
            );
        }

        return true;
    }
}
