
package com.flashseat.booking.controller;

import com.flashseat.booking.dto.*;
import com.flashseat.booking.service.SeatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping("/api/seats")
    public ResponseEntity<SeatResponse> createSeat(
            @Valid @RequestBody CreateSeatRequest request) {

        SeatResponse response = seatService.createSeat(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/api/shows/{showId}/seats")
    public ResponseEntity<ShowSeatResponse> addSeatToShow(
            @PathVariable Long showId,
            @Valid @RequestBody AddShowSeatRequest request) {

        ShowSeatResponse response =
                seatService.addSeatToShow(showId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/api/shows/{showId}/seats")
    public ResponseEntity<List<ShowSeatResponse>> getSeatsByShowId(
            @PathVariable Long showId) {

        return ResponseEntity.ok(
                seatService.getSeatsByShowId(showId)
        );
    }

}
