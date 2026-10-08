package com.Leon.accommodation_finder.model;

// every payment starts as PENDING and ends as PAID or FAILED, it never changes after that
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED
}
