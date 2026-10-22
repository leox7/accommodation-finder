package com.Leon.accommodation_finder.exception;

public class ReviewAlreadyExistsException extends RuntimeException {
    public ReviewAlreadyExistsException() {
        super("You have already reviewed this listing");
    }
}
