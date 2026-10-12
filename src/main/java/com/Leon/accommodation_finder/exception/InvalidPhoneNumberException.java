package com.Leon.accommodation_finder.exception;

// the message never repeats the number the user typed, so it cannot end up in a log line
public class InvalidPhoneNumberException extends RuntimeException {
    public InvalidPhoneNumberException() {
        super("Enter a valid Safaricom number, for example 0712345678");
    }
}
