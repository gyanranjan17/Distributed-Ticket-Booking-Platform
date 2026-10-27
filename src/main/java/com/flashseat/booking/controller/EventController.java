package com.flashseat.booking.controller;

import com.flashseat.booking.dto.CreateEventRequest;
import com.flashseat.booking.dto.EventResponse;
import com.flashseat.booking.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request){

        EventResponse createdEvent = eventService.createEvent(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdEvent);
    }

    @GetMapping
    public List<EventResponse> getAllEvents(){
        return eventService.getAllEvents();
    }

    @GetMapping("/{id}")
    public EventResponse getEventId(@PathVariable Long id){
        return eventService.getEventById(id);
    }
}
