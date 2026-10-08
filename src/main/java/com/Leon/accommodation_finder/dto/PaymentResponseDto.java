package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.PaymentType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// What the API returns for a payment, never the Payment entity itself
@Getter
@Setter
@NoArgsConstructor
public class PaymentResponseDto {

    private Long id;
    private Long bookingId;
    private String listingTitle;
    private PaymentType type;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentProviderType provider;
    // always masked, for example 2547****678
    private String phoneNumber;
    // empty until M-Pesa payments exist (part 2)
    private String mpesaReceiptNumber;
    private LocalDateTime dateCreated;
}
