package com.flashseat.booking.service;

import com.flashseat.booking.repository.BookingRepository;
import com.flashseat.booking.repository.ShowSeatRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.flashseat.booking.entity.Booking;
import com.flashseat.booking.entity.BookingStatus;
import com.flashseat.booking.entity.SeatStatus;
import com.flashseat.booking.entity.ShowSeat;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ShowSeatRepository showSeatRepository;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        // Test setup will be added if needed.
    }

    @Test
    void confirmBooking_success() {

        // Arrange: Create a mock booking and associated seat
        Long bookingId = 101L;
        Long showSeatId = 25L;

        ShowSeat showSeat = new ShowSeat();
        showSeat.setId(showSeatId);
        showSeat.setStatus(SeatStatus.LOCKED);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setShowSeat(showSeat);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        // Mock the initial findById()
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        // Mock successful booking status update
        when(bookingRepository.confirmBookingIfPendingAndNotExpired(
                eq(bookingId),
                eq(BookingStatus.PENDING),
                eq(BookingStatus.CONFIRMED),
                any(LocalDateTime.class)
        )).thenReturn(1);

        // Mock successful seat status update
        when(showSeatRepository.updateSeatStatusIfMatch(
                showSeatId,
                SeatStatus.LOCKED,
                SeatStatus.BOOKED
        )).thenReturn(1);

        // Act: Execute the method being tested
        bookingService.confirmBooking(bookingId);

        // Assert: Verify both updates were executed
        verify(bookingRepository, times(1))
                .confirmBookingIfPendingAndNotExpired(
                        eq(bookingId),
                        eq(BookingStatus.PENDING),
                        eq(BookingStatus.CONFIRMED),
                        any(LocalDateTime.class)
                );

        verify(showSeatRepository, times(1))
                .updateSeatStatusIfMatch(
                        showSeatId,
                        SeatStatus.LOCKED,
                        SeatStatus.BOOKED
                );
    }

    @Test
    void confirmBooking_cancelledBooking_shouldFail() {

        // Arrange
        Long bookingId = 103L;
        Long showSeatId = 27L;

        ShowSeat showSeat = new ShowSeat();
        showSeat.setId(showSeatId);
        showSeat.setStatus(SeatStatus.AVAILABLE);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setShowSeat(showSeat);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        // Database rejects the transition because status is CANCELLED
        when(bookingRepository.confirmBookingIfPendingAndNotExpired(
                eq(bookingId),
                eq(BookingStatus.PENDING),
                eq(BookingStatus.CONFIRMED),
                any(LocalDateTime.class)
        )).thenReturn(0);

        // Act + Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bookingService.confirmBooking(bookingId)
        );

        assertEquals(
                "Booking cannot be confirmed: " + bookingId,
                exception.getMessage()
        );

        // Confirm that the seat update was never attempted
        verify(showSeatRepository, never())
                .updateSeatStatusIfMatch(
                        any(Long.class),
                        any(SeatStatus.class),
                        any(SeatStatus.class)
                );
    }

    @Test
    void confirmBooking_alreadyConfirmed_shouldFail() {

        // Arrange
        Long bookingId = 104L;
        Long showSeatId = 28L;

        ShowSeat showSeat = new ShowSeat();
        showSeat.setId(showSeatId);
        showSeat.setStatus(SeatStatus.BOOKED);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setShowSeat(showSeat);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        // Conditional UPDATE rejects CONFIRMED → CONFIRMED
        when(bookingRepository.confirmBookingIfPendingAndNotExpired(
                eq(bookingId),
                eq(BookingStatus.PENDING),
                eq(BookingStatus.CONFIRMED),
                any(LocalDateTime.class)
        )).thenReturn(0);

        // Act + Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bookingService.confirmBooking(bookingId)
        );

        assertEquals(
                "Booking cannot be confirmed: " + bookingId,
                exception.getMessage()
        );

        // The seat must not be updated again
        verify(showSeatRepository, never())
                .updateSeatStatusIfMatch(
                        any(Long.class),
                        any(SeatStatus.class),
                        any(SeatStatus.class)
                );
    }

    @Test
    void confirmBooking_seatUpdateFails_shouldThrowException() {

        // Arrange
        Long bookingId = 105L;
        Long showSeatId = 29L;

        ShowSeat showSeat = new ShowSeat();
        showSeat.setId(showSeatId);
        showSeat.setStatus(SeatStatus.LOCKED);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setShowSeat(showSeat);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        // Booking status update succeeds
        when(bookingRepository.confirmBookingIfPendingAndNotExpired(
                eq(bookingId),
                eq(BookingStatus.PENDING),
                eq(BookingStatus.CONFIRMED),
                any(LocalDateTime.class)
        )).thenReturn(1);

        // Seat status update fails
        when(showSeatRepository.updateSeatStatusIfMatch(
                showSeatId,
                SeatStatus.LOCKED,
                SeatStatus.BOOKED
        )).thenReturn(0);

        // Act + Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bookingService.confirmBooking(bookingId)
        );

        assertEquals(
                "Failed to mark seat as booked: " + bookingId,
                exception.getMessage()
        );

        verify(bookingRepository, times(1))
                .confirmBookingIfPendingAndNotExpired(
                        eq(bookingId),
                        eq(BookingStatus.PENDING),
                        eq(BookingStatus.CONFIRMED),
                        any(LocalDateTime.class)
                );

        verify(showSeatRepository, times(1))
                .updateSeatStatusIfMatch(
                        showSeatId,
                        SeatStatus.LOCKED,
                        SeatStatus.BOOKED
                );
    }



}
