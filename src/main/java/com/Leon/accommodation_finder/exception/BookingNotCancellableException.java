package com.Leon.accommodation_finder.exception;

public class BookingNotCancellableException extends RuntimeException {
    public BookingNotCancellableException(String status) {
        super("Only PENDING bookings can be cancelled. This booking is already " + status);
    }
}
