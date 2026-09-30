package com.Leon.accommodation_finder.exception;

public class PrimaryImageExistsException extends RuntimeException {
    public PrimaryImageExistsException() {
        super("This listing already has a primary image. Delete it first or upload this one as a secondary image");
    }
}
