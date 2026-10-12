package com.Leon.accommodation_finder.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// The counts on the admin dashboard. Serializable so Spring can store it in the Redis cache.
@Getter
@Setter
@NoArgsConstructor
public class AdminOverviewDto implements Serializable {

    private long totalUsers;
    private long totalListings;
    private long totalBookings;
    private long totalPayments;
    // when the counts were taken from the database. The same time on two calls means the second came from the cache
    private LocalDateTime generatedAt;
}
