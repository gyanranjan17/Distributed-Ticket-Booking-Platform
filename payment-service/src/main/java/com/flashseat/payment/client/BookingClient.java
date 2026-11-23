package com.flashseat.payment.client;

import com.flashseat.payment.dto.BookingResponse;
import com.flashseat.payment.exception.BookingNotFoundException;
import com.flashseat.payment.exception.BookingServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class BookingClient {

    private final RestClient bookingRestClient;


    public BookingResponse getBooking(Long bookingId) {

        try {
            BookingResponse response = bookingRestClient.get()
                    .uri("/api/bookings/{bookingId}", bookingId)
                    .retrieve()
                    .body(BookingResponse.class);

            if (response == null) {
                throw new BookingServiceUnavailableException(
                        new IllegalStateException(
                                "Empty response from Booking Service"
                        )
                );
            }

            return response;

        } catch (HttpClientErrorException.NotFound ex) {
            throw new BookingNotFoundException(bookingId);

        } catch (RestClientException ex) {
            throw new BookingServiceUnavailableException(ex);
        }
    }

}

