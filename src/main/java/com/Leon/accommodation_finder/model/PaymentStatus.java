package com.Leon.accommodation_finder.model;

// every payment starts as PENDING and ends as PAID or FAILED, it never changes after that.
// UNKNOWN (M-Pesa only): we asked Safaricom 3 times and got no definite answer, so the money may or
// may not have moved. It is still checked for up to 1 hour and then becomes PAID or FAILED.
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    UNKNOWN
}
