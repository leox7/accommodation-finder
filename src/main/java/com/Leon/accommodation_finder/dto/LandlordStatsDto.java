package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// The landlord dashboard numbers. Income is the sum of PAID payments on this landlord's listings.
@Getter
@Setter
@NoArgsConstructor
public class LandlordStatsDto {

    private long activeListings;
    private long pendingRequests;
    private BigDecimal incomeThisMonth;
    private BigDecimal incomeLastMonth;
}
