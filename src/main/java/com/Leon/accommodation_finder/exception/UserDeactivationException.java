package com.Leon.accommodation_finder.exception;

// 409 when a user cannot be deactivated, each place passes its own clear message
public class UserDeactivationException extends RuntimeException {
    public UserDeactivationException(String message) {
        super(message);
    }
}
