package com.Leon.accommodation_finder.exception;

public class ListingImageNotFoundException extends RuntimeException {
    public ListingImageNotFoundException(Long imageId) {
        super("Image not found with id: " + imageId + " on this listing");
    }
}
