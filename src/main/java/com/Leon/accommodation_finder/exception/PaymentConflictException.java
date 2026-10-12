package com.Leon.accommodation_finder.exception;

// one exception for every payment rule that gives 409, each place passes its own clear message
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) {
        super(message);
    }
}
