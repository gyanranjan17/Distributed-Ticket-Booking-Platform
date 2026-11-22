package com.flashseat.booking.integration;

import com.flashseat.booking.service.BookingService;
import com.flashseat.booking.repository.BookingRepository;
import com.flashseat.booking.repository.ShowSeatRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.flashseat.booking.entity.*;
import com.flashseat.booking.repository.EventRepository;
import com.flashseat.booking.repository.ShowRepository;
import com.flashseat.booking.repository.SeatRepository;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringBootTest
@Testcontainers
class BookingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long bookingId;
    private Long showSeatId;

    @Test
    void contextLoads() {
        // Verify that Spring Boot starts with PostgreSQL.
    }

    @BeforeEach
    void setUp() {

        // 1. Create Event
        Event event = new Event();
        event.setName("Integration Test Concert");
        event.setVenue("Test Arena");
        event.setTotalSeats(100);
        event.setAvailableSeats(100);

        event = eventRepository.saveAndFlush(event);

        // 2. Create Show
        Show show = new Show();
        show.setEvent(event);
        show.setStartTime(LocalDateTime.now().plusDays(1));
        show.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));

        show = showRepository.saveAndFlush(show);

        // 3. Create Seat
        Seat seat = new Seat();
        seat.setSeatNumber("A1");
        seat.setRowLabel("A");

        seat = seatRepository.saveAndFlush(seat);

        // 4. Create ShowSeat
        ShowSeat showSeat = new ShowSeat();
        showSeat.setShow(show);
        showSeat.setSeat(seat);
        showSeat.setStatus(SeatStatus.LOCKED);
        showSeat.setPrice(new BigDecimal("500.00"));

        showSeat = showSeatRepository.saveAndFlush(showSeat);
        showSeatId = showSeat.getId();

        // 5. Create Booking
        Booking booking = new Booking();
        booking.setShowSeat(showSeat);
        booking.setUserId(101L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setAmount(new BigDecimal("500.00"));

        booking = bookingRepository.saveAndFlush(booking);
        bookingId = booking.getId();
    }

    @Test
    void confirmBooking_seatUpdateFails_shouldRollback() {

        // Arrange: Change the seat to AVAILABLE
        // so the LOCKED → BOOKED update will fail.
        Integer updatedRows = transactionTemplate.execute(status ->
                showSeatRepository.updateSeatStatusIfMatch(
                        showSeatId,
                        SeatStatus.LOCKED,
                        SeatStatus.AVAILABLE
                )
        );

        assertEquals(1, updatedRows);

        // Act: Attempt to confirm the booking.
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bookingService.confirmBooking(bookingId)
        );

        assertEquals(
                "Failed to mark seat as booked: " + bookingId,
                exception.getMessage()
        );

        // Assert: Read the booking from PostgreSQL.
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow();

        ShowSeat showSeat = showSeatRepository.findById(showSeatId)
                .orElseThrow();

        // Booking confirmation must have rolled back.
        assertEquals(BookingStatus.PENDING, booking.getStatus());

        // Seat remains AVAILABLE because we changed it
        // before the confirmation transaction started.
        assertEquals(SeatStatus.AVAILABLE, showSeat.getStatus());
    }

    @Test
    void confirmBooking_success_shouldPersistBothUpdates() {

        // Arrange: Verify initial database state
        Booking initialBooking = bookingRepository.findById(bookingId)
                .orElseThrow();

        ShowSeat initialSeat = showSeatRepository.findById(showSeatId)
                .orElseThrow();

        assertEquals(
                BookingStatus.PENDING,
                initialBooking.getStatus()
        );

        assertEquals(
                SeatStatus.LOCKED,
                initialSeat.getStatus()
        );

        // Act: Confirm the booking
        bookingService.confirmBooking(bookingId);

        // Assert: Read the updated records from PostgreSQL
        Booking confirmedBooking = bookingRepository.findById(bookingId)
                .orElseThrow();

        ShowSeat bookedSeat = showSeatRepository.findById(showSeatId)
                .orElseThrow();

        assertEquals(
                BookingStatus.CONFIRMED,
                confirmedBooking.getStatus()
        );

        assertEquals(
                SeatStatus.BOOKED,
                bookedSeat.getStatus()
        );
    }

    @Test
    void confirmBooking_expiredBooking_shouldFail() {

        // Arrange: Set the booking expiration to the past
        transactionTemplate.executeWithoutResult(status -> {

            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow();

            booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));

            bookingRepository.saveAndFlush(booking);
        });

        // Act: Attempt confirmation after expiration
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bookingService.confirmBooking(bookingId)
        );

        assertEquals(
                "Booking cannot be confirmed: " + bookingId,
                exception.getMessage()
        );

        // Assert: Verify database state
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow();

        ShowSeat showSeat = showSeatRepository.findById(showSeatId)
                .orElseThrow();

        assertEquals(
                BookingStatus.PENDING,
                booking.getStatus()
        );

        assertEquals(
                SeatStatus.LOCKED,
                showSeat.getStatus()
        );
    }


}
