package com.Leon.accommodation_finder.exception;

public class ListingNotAvailableException extends RuntimeException {
    public ListingNotAvailableException() {
        super("This listing is already booked for some of the dates you chose. Please pick different dates");
    }
}
