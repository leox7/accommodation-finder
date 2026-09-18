package com.Leon.accommodation_finder.exception;

public class DuplicateListingTitleException extends RuntimeException {
    public DuplicateListingTitleException(String title) {
        super("A listing with the title '" + title + "' already exists");
    }
}
