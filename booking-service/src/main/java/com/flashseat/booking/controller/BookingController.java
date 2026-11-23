package com.flashseat.booking.controller;

import com.flashseat.booking.dto.BookingResponse;
import com.flashseat.booking.dto.CreateBookingRequest;
import com.flashseat.booking.service.BookingService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {

        BookingResponse response =
                bookingService.createBooking(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long bookingId) {

        bookingService.cancelBooking(bookingId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long bookingId) {
        BookingResponse response = bookingService.
                getBookingById(bookingId);
        return ResponseEntity.ok(response);
    }

}
