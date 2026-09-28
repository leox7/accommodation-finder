package com.Leon.accommodation_finder.exception;

public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException() {
        super("The OTP is invalid or has expired, please request a new one");
    }
}
