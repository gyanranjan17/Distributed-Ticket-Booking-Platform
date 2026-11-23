package com.flashseat.payment.exception;

public class BookingServiceUnavailableException extends RuntimeException {

    public BookingServiceUnavailableException(Throwable cause) {
        super("Booking Service is temporarily unavailable", cause);
    }
}