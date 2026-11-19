package com.flashseat.booking.service;

import com.flashseat.booking.dto.CreateShowRequest;
import com.flashseat.booking.dto.ShowResponse;
import com.flashseat.booking.entity.Event;
import com.flashseat.booking.entity.Show;
import com.flashseat.booking.exception.ResourceNotFoundException;
import com.flashseat.booking.repository.EventRepository;
import com.flashseat.booking.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowService {
    private final ShowRepository showRepository;
    private final EventRepository eventRepository;

    public ShowResponse createShow(
            Long eventId,
            CreateShowRequest request
    ){
        // Check whether the event exists
        Event event = eventRepository.findById(eventId)
                .orElseThrow(()-> new ResourceNotFoundException(
                        "Event not found with id: " + eventId
                ));

        // Validate show timings
        if(!request.getEndTime().isAfter(request.getStartTime())){
            throw new IllegalArgumentException(
                    "End time must be after start time"
            );
        }

        //Create the show entity
        Show show = new Show();
        show.setEvent(event);
        show.setStartTime(request.getStartTime());
        show.setEndTime(request.getEndTime());

        //Save to PostgreSQL
        Show savedShow = showRepository.save(show);

        //Return response DTO
        return mapToResponse(savedShow);
    }

    public List<ShowResponse> getShowsByEventId(Long eventId){
        if(!eventRepository.existsById(eventId)){
            throw new ResourceNotFoundException(
                    "Event not found with id: " + eventId
            );
        }
        return showRepository.findByEventId(eventId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ShowResponse mapToResponse(Show show){
        return new ShowResponse(
                show.getId(),
                show.getEvent().getId(),
                show.getStartTime(),
                show.getEndTime()
        );
    }
}
