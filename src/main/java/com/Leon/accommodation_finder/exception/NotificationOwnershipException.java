package com.Leon.accommodation_finder.exception;

public class NotificationOwnershipException extends RuntimeException {
    public NotificationOwnershipException() {
        super("You can only manage your own notifications");
    }
}
