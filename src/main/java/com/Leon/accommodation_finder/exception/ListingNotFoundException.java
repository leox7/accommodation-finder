package com.Leon.accommodation_finder.exception;

public class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException(Long id) {
        super("Listing not found with id: " + id);
    }

}

