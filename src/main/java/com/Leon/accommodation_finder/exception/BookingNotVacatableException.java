package com.Leon.accommodation_finder.exception;

public class BookingNotVacatableException extends RuntimeException {
    public BookingNotVacatableException(String status) {
        super("Only CONFIRMED bookings can be vacated. This booking is " + status);
    }
}
