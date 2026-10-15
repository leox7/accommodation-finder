package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// The admin dashboard numbers. Payments received are PAID payments only.
@Getter
@Setter
@NoArgsConstructor
public class AdminStatsDto {

    private long totalUsers;
    private long totalListings;
    private long newListingsThisMonth;
    private long pendingBookings;
    private BigDecimal paymentsReceivedThisMonth;
    private BigDecimal paymentsReceivedLastMonth;
}
