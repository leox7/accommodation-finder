package com.Leon.accommodation_finder.exception;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
        super("Image is too large. The maximum size is 2MB");
    }
}
