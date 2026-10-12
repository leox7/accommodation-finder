package com.Leon.accommodation_finder.dto;

import com.Leon.accommodation_finder.model.ReportGroupBy;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// One report, used for both the landlord report and the admin report.
// Only counts and sums: never a name, email, phone number or national ID.
@Getter
@Setter
@NoArgsConstructor
public class ReportResponseDto {

    // the date range that was used, after filling in the defaults
    private LocalDate from;
    private LocalDate to;
    private ReportGroupBy groupBy;

    // how many listings there are right now, not filtered by date
    private ListingCounts listings;
    // bookings created in the date range, one entry per status or per month
    private List<BookingGroup> bookings;
    // payments created in the date range, one entry per status or per month
    private List<PaymentGroup> payments;
    // the sum of PAID payments in the date range. It ignores paymentStatus, income is always paid money
    private BigDecimal income;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ListingCounts {
        private long total;
        private long available;
        private long occupied;
    }

    // group is a status name like CONFIRMED, or a month like 2026-10
    @Getter
    @Setter
    @NoArgsConstructor
    public static class BookingGroup {
        private String group;
        private long count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PaymentGroup {
        private String group;
        private long count;
        private BigDecimal totalAmount;
    }
}
