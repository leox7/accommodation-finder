package com.Leon.accommodation_finder.exception;

public class ReviewNotAllowedException extends RuntimeException {
    public ReviewNotAllowedException() {
        super("You can only review a listing after your stay there has ended");
    }
}
