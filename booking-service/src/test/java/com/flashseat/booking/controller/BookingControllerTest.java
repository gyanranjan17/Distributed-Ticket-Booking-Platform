package com.flashseat.booking.controller;

import com.flashseat.booking.dto.BookingResponse;
import com.flashseat.booking.entity.BookingStatus;
import com.flashseat.booking.exception.BookingNotFoundException;
import com.flashseat.booking.service.BookingService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void existingBookingShouldReturn200() throws Exception {

        BookingResponse response = new BookingResponse(
                101L,
                10L,
                1L,
                BookingStatus.PENDING,
                new BigDecimal("500.00"),
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10)
        );

        when(bookingService.getBookingById(101L))
                .thenReturn(response);

        mockMvc.perform(get("/api/bookings/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(101))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(500.0));

        verify(bookingService).getBookingById(101L);
    }

    @Test
    void missingBookingShouldReturn404() throws Exception {

        when(bookingService.getBookingById(999L))
                .thenThrow(new BookingNotFoundException(999L));

        mockMvc.perform(get("/api/bookings/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Booking not found: 999"));

        verify(bookingService).getBookingById(999L);
    }
}
