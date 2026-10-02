package com.Leon.accommodation_finder.exception;

public class StudentHasActiveBookingException extends RuntimeException {
    public StudentHasActiveBookingException() {
        super("You already have a pending or confirmed booking that overlaps these dates. Cancel it or choose dates after it ends");
    }
}
