package com.Leon.accommodation_finder.exception;

public class ReviewOwnershipException extends RuntimeException {
    public ReviewOwnershipException() {
        super("You can only change or delete your own reviews");
    }
}
