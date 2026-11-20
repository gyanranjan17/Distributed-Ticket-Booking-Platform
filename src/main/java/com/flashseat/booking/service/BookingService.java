package com.flashseat.booking.service;

import com.flashseat.booking.dto.BookingResponse;
import com.flashseat.booking.dto.CreateBookingRequest;
import com.flashseat.booking.entity.*;
import com.flashseat.booking.exception.ResourceNotFoundException;
import com.flashseat.booking.repository.BookingRepository;
import com.flashseat.booking.repository.ShowSeatRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {

        // Step 1: Find the show seat
        ShowSeat showSeat = showSeatRepository
                .findById(request.getShowSeatId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Show seat not found: " + request.getShowSeatId()
                ));

        // Step 2: Atomically reserve the seat
        int updatedRows = showSeatRepository.updateSeatStatusIfMatch(
                showSeat.getId(),
                SeatStatus.AVAILABLE,
                SeatStatus.LOCKED
        );

        // Step 3: Reject if the seat is unavailable
        if (updatedRows == 0) {
            throw new IllegalStateException(
                    "Seat is already reserved or booked"
            );
        }

        // Step 4: Create a pending booking
        Booking booking = new Booking();
        booking.setShowSeat(showSeat);
        booking.setUserId(request.getUserId());
        booking.setStatus(BookingStatus.PENDING);
        booking.setAmount(showSeat.getPrice());

        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        // Step 5: Return the booking response
        return new BookingResponse(
                savedBooking.getId(),
                showSeat.getId(),
                savedBooking.getUserId(),
                savedBooking.getStatus(),
                savedBooking.getAmount(),
                savedBooking.getCreatedAt()
        );
    }
}
