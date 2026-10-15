package com.Leon.accommodation_finder.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User with id " + id + " was not found");
    }

    // used when the logged in user's account no longer exists
    public UserNotFoundException() {
        super("User account was not found");
    }
}
