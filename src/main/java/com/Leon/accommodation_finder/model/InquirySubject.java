package com.Leon.accommodation_finder.model;

import com.fasterxml.jackson.annotation.JsonValue;

// The topics on the contact form. The JSON uses the readable label (for example "Booking help"),
// the database stores the enum name (for example BOOKING_HELP).
public enum InquirySubject {
    GENERAL_QUESTION("General question"),
    BOOKING_HELP("Booking help"),
    PAYMENT_HELP("Payment help"),
    LANDLORD_ACCOUNT("Landlord account"),
    REPORT_A_PROBLEM("Report a problem");

    private final String label;

    InquirySubject(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
