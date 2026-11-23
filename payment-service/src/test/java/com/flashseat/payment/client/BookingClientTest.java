package com.flashseat.payment.client;

import com.flashseat.payment.dto.BookingResponse;
import com.flashseat.payment.exception.BookingNotFoundException;
import com.flashseat.payment.exception.BookingServiceUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BookingClientTest {

    private BookingClient bookingClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8080");

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        bookingClient = new BookingClient(builder.build());
    }

    @Test
    void validBookingShouldReturnBookingResponse() {

        mockServer.expect(requestTo(
                        "http://localhost:8080/api/bookings/101"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "bookingId": 101,
                            "showSeatId": 10,
                            "userId": 1,
                            "status": "PENDING",
                            "amount": 500.00,
                            "createdAt": "2026-11-23T16:30:00",
                            "expiresAt": "2026-11-23T16:40:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        BookingResponse response =
                bookingClient.getBooking(101L);

        assertNotNull(response);
        assertEquals(101L, response.bookingId());
        assertEquals("PENDING", response.status());

        mockServer.verify();
    }

    @Test
    void bookingNotFoundShouldThrow404Exception() {

        mockServer.expect(requestTo(
                        "http://localhost:8080/api/bookings/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(
                BookingNotFoundException.class,
                () -> bookingClient.getBooking(999L)
        );

        mockServer.verify();
    }

    @Test
    void bookingService500ShouldThrowUnavailableException() {

        mockServer.expect(requestTo(
                        "http://localhost:8080/api/bookings/101"))
                .andRespond(withServerError());

        assertThrows(
                BookingServiceUnavailableException.class,
                () -> bookingClient.getBooking(101L)
        );

        mockServer.verify();
    }

    @Test
    void emptyResponseShouldThrowUnavailableException() {

        mockServer.expect(requestTo(
                        "http://localhost:8080/api/bookings/101"))
                .andRespond(withSuccess());

        assertThrows(
                BookingServiceUnavailableException.class,
                () -> bookingClient.getBooking(101L)
        );

        mockServer.verify();
    }

    @Test
    void connectionFailureShouldThrowUnavailableException() {

        mockServer.expect(requestTo(
                        "http://localhost:8080/api/bookings/101"))
                .andRespond(request -> {
                    throw new java.io.IOException(
                            "Simulated connection failure"
                    );
                });

        assertThrows(
                BookingServiceUnavailableException.class,
                () -> bookingClient.getBooking(101L)
        );

        mockServer.verify();
    }
}
