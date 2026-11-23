
package com.flashseat.booking.dto;

import com.flashseat.booking.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ShowSeatResponse {

    private Long id;
    private Long showId;
    private Long seatId;
    private String seatNumber;
    private String rowLabel;
    private SeatStatus status;
    private BigDecimal price;
}
