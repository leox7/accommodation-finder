package com.Leon.accommodation_finder.exception;

public class ListingHasActiveBookingsException extends RuntimeException {
    public ListingHasActiveBookingsException() {
        super("This listing cannot be deleted because it has pending or confirmed bookings");
    }
}
