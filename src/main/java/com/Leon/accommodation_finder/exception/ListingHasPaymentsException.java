package com.Leon.accommodation_finder.exception;

public class ListingHasPaymentsException extends RuntimeException {
    public ListingHasPaymentsException() {
        super("This listing cannot be deleted because it has payment history");
    }
}
