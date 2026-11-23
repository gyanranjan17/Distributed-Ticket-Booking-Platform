package com.flashseat.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ShowResponse {
    private Long id;
    private Long eventId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

}
