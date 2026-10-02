package com.Leon.accommodation_finder.exception;

public class BookingListingOwnershipException extends RuntimeException {
    public BookingListingOwnershipException() {
        super("You can only manage bookings on your own listings");
    }
}
