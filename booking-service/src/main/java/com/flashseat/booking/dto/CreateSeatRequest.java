package com.flashseat.booking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSeatRequest {

    @NotBlank(message = "Seat number is required")
    private String seatNumber;

    @NotBlank(message = "Row label is required")
    private String rowLabel;
}
