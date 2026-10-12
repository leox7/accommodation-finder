package com.Leon.accommodation_finder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One attempt by a student to pay a deposit or a month's rent for a confirmed booking.
// Every field is set by the server, never taken from the request.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    // money is always BigDecimal, a double can turn 0.1 + 0.2 into 0.30000000000000004
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProviderType provider;

    // the full normalized number, for example 254712345678. Only ever shown masked
    @Column(nullable = false)
    private String phoneNumber;

    // filled in by M-Pesa (part 2), empty for simulated payments
    private String merchantRequestId;
    private String checkoutRequestId;
    private String mpesaReceiptNumber;

    // how many times the STK Push Query fallback has asked Safaricom about this payment (at most 3).
    // The database default of 0 lets the column be added to a table that already has rows.
    @Column(nullable = false, columnDefinition = "int default 0")
    private int statusChecks;

    // when the last STK Push Query ran, empty until the first one. UNKNOWN payments are asked again 10 minutes after it
    private LocalDateTime lastCheckedAt;

    private LocalDateTime dateCreated;
}
