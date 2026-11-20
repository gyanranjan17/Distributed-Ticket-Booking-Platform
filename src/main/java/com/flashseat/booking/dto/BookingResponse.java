package com.flashseat.booking.dto;

import com.flashseat.booking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BookingResponse {

    private Long bookingId;
    private Long showSeatId;
    private Long userId;
    private BookingStatus status;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
