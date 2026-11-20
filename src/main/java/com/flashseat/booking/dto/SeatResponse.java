package com.flashseat.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeatResponse {

    private Long id;
    private String seatNumber;
    private String rowLabel;
}
