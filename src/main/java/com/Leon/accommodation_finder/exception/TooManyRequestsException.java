package com.Leon.accommodation_finder.exception;

// Thrown when someone repeats an action too quickly (resend OTP, send inquiry).
// retryAfterSeconds goes into the Retry-After header so the frontend can show a countdown.
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
