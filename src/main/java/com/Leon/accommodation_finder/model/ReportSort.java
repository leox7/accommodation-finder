package com.Leon.accommodation_finder.model;

// the order of the bookings and payments group lists in a report:
// by group name (a status, or a month like 2026-10, where ascending means oldest first) or by count
public enum ReportSort {
    GROUP_ASC,
    GROUP_DESC,
    COUNT_ASC,
    COUNT_DESC
}
