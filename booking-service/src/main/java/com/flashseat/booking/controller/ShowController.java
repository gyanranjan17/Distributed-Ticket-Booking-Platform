package com.flashseat.booking.controller;

import com.flashseat.booking.dto.CreateShowRequest;
import com.flashseat.booking.dto.ShowResponse;
import com.flashseat.booking.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/events/{eventId}/shows")
@RequiredArgsConstructor
public class ShowController {
    private final ShowService showService;

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateShowRequest request
    ){
        ShowResponse response = showService.createShow(eventId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public List<ShowResponse> getShowsByEventId(
            @PathVariable Long eventId) {

        return showService.getShowsByEventId(eventId);
    }
}
