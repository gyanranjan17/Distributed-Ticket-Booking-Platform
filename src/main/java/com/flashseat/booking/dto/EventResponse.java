package com.flashseat.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String name;
    private String venue;
    private Integer totalSeats;
    private Integer availableSeats;
}
