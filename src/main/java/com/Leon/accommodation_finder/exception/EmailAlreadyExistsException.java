package com.Leon.accommodation_finder.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("An account with the email '" + email + "' already exists");
    }
}