package com.Leon.accommodation_finder.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long id) {
        super("Booking not found with id: " + id);
    }
}
