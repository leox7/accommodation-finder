package com.Leon.accommodation_finder.exception;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(Long id) {
        super("Review not found with id: " + id);
    }
}
