package com.Leon.accommodation_finder.exception;

public class BookingNotConfirmableException extends RuntimeException {
    public BookingNotConfirmableException(String status) {
        super("Only PENDING bookings can be confirmed. This booking is already " + status);
    }
}
