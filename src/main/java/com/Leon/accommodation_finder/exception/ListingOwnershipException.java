package com.Leon.accommodation_finder.exception;

public class ListingOwnershipException extends RuntimeException {
    public ListingOwnershipException() {
        super("You can only edit or delete your own listings");
    }
}