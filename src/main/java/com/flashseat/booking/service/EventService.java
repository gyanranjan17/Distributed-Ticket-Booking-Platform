package com.flashseat.booking.service;


import com.flashseat.booking.dto.CreateEventRequest;
import com.flashseat.booking.dto.EventResponse;
import com.flashseat.booking.entity.Event;
import com.flashseat.booking.exception.ResourceNotFoundException;
import com.flashseat.booking.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;

    public EventResponse createEvent(CreateEventRequest request){
        Event event = new Event();

        event.setName(request.getName());
        event.setVenue(request.getVenue());
        event.setTotalSeats(request.getTotalSeats());
        event.setAvailableSeats(request.getTotalSeats());

        Event savedEvent = eventRepository.save(event);

        return mapToResponse(savedEvent);
    }

    public List<EventResponse> getAllEvents(){
        return eventRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public EventResponse getEventById(Long id){
        Event event = eventRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Event not found with id: " + id
                        ));

        return mapToResponse(event);
    }

    private EventResponse mapToResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getTotalSeats(),
                event.getAvailableSeats()
        );
    }

}
