package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.PaymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

// What a student sends to pay. There is deliberately NO amount, status or provider here:
// the amount comes from the listing price, the rest is set by the server.
// An extra "amount" field in the JSON is simply ignored.
@Getter
@Setter
@NoArgsConstructor
public class PaymentRequestDto {

    @NotNull(message = "Booking id is required")
    private Long bookingId;

    @NotNull(message = "Payment type is required (DEPOSIT or RENT)")
    private PaymentType type;

    // only checked for blank here, PhoneUtil.normalize does the real check in the service
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}
