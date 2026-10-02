package com.Leon.accommodation_finder.exception;

public class BookingOwnershipException extends RuntimeException {
    public BookingOwnershipException() {
        super("You can only view or change your own bookings");
    }
}
