package com.Leon.accommodation_finder.exception;

public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException() {
        super("Only JPG or PNG images are allowed");
    }
}
