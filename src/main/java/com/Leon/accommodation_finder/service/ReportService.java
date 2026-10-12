package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.ReportResponseDto;
import com.Leon.accommodation_finder.exception.InvalidReportParametersException;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.ListingStatus;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.ReportGroupBy;
import com.Leon.accommodation_finder.model.ReportSort;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import com.Leon.accommodation_finder.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Report DATA only (JSON). Every option the user chooses arrives as a typed parameter (a date or an enum),
// and the queries are fixed @Query methods: user input is never joined into a query string.
@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    // used when "from" is missing, early enough to include everything
    private static final LocalDate DEFAULT_FROM = LocalDate.of(2000, 1, 1);

    private final ListingRepository listingRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @Autowired
    public ReportService(ListingRepository listingRepository, BookingRepository bookingRepository,
                         PaymentRepository paymentRepository) {
        this.listingRepository = listingRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    // landlordEmail is the logged in landlord, taken from their JWT. Every query is limited to their
    // own listings, so a landlord can never see another landlord's numbers.
    public ReportResponseDto getLandlordReport(String landlordEmail, LocalDate from, LocalDate to,
                                               BookingStatus bookingStatus, PaymentStatus paymentStatus,
                                               ReportGroupBy groupBy, ReportSort sort) {
        try {
            LocalDate start = (from == null) ? DEFAULT_FROM : from;
            LocalDate end = (to == null) ? LocalDate.now() : to;
            checkDates(start, end);
            // the whole of the "to" day is included: dateCreated >= start of "from" AND < start of the day after "to"
            LocalDateTime fromTime = start.atStartOfDay();
            LocalDateTime toTime = end.plusDays(1).atStartOfDay();

            ReportResponseDto.ListingCounts listings = new ReportResponseDto.ListingCounts();
            listings.setTotal(listingRepository.countLandlordListings(landlordEmail, null));
            listings.setAvailable(listingRepository.countLandlordListings(landlordEmail, ListingStatus.AVAILABLE));
            listings.setOccupied(listingRepository.countLandlordListings(landlordEmail, ListingStatus.OCCUPIED));

            List<Object[]> bookingRows;
            List<Object[]> paymentRows;
            if (groupBy == ReportGroupBy.MONTH) {
                bookingRows = bookingRepository.countLandlordBookingsByMonth(landlordEmail, fromTime, toTime, bookingStatus);
                paymentRows = paymentRepository.sumLandlordPaymentsByMonth(landlordEmail, fromTime, toTime, paymentStatus);
            } else {
                bookingRows = bookingRepository.countLandlordBookingsByStatus(landlordEmail, fromTime, toTime, bookingStatus);
                paymentRows = paymentRepository.sumLandlordPaymentsByStatus(landlordEmail, fromTime, toTime, paymentStatus);
            }
            BigDecimal income = paymentRepository.sumLandlordIncome(landlordEmail, fromTime, toTime);

            return buildReport(start, end, groupBy, sort, listings, bookingRows, paymentRows, income);
        } catch (Exception e) {
            logger.error("Error while building the report for the logged in landlord: {}", e.getMessage());
            throw e;
        }
    }

    // the same report for the whole system, for admins
    public ReportResponseDto getAdminReport(LocalDate from, LocalDate to, BookingStatus bookingStatus,
                                            PaymentStatus paymentStatus, ReportGroupBy groupBy,
                                            ReportSort sort) {
        try {
            LocalDate start = (from == null) ? DEFAULT_FROM : from;
            LocalDate end = (to == null) ? LocalDate.now() : to;
            checkDates(start, end);
            LocalDateTime fromTime = start.atStartOfDay();
            LocalDateTime toTime = end.plusDays(1).atStartOfDay();

            ReportResponseDto.ListingCounts listings = new ReportResponseDto.ListingCounts();
            listings.setTotal(listingRepository.countAllListings(null));
            listings.setAvailable(listingRepository.countAllListings(ListingStatus.AVAILABLE));
            listings.setOccupied(listingRepository.countAllListings(ListingStatus.OCCUPIED));

            List<Object[]> bookingRows;
            List<Object[]> paymentRows;
            if (groupBy == ReportGroupBy.MONTH) {
                bookingRows = bookingRepository.countAllBookingsByMonth(fromTime, toTime, bookingStatus);
                paymentRows = paymentRepository.sumAllPaymentsByMonth(fromTime, toTime, paymentStatus);
            } else {
                bookingRows = bookingRepository.countAllBookingsByStatus(fromTime, toTime, bookingStatus);
                paymentRows = paymentRepository.sumAllPaymentsByStatus(fromTime, toTime, paymentStatus);
            }
            BigDecimal income = paymentRepository.sumAllIncome(fromTime, toTime);

            return buildReport(start, end, groupBy, sort, listings, bookingRows, paymentRows, income);
        } catch (Exception e) {
            logger.error("Error while building the admin report: {}", e.getMessage());
            throw e;
        }
    }

    private void checkDates(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new InvalidReportParametersException("'from' must be on or before 'to'");
        }
    }

    // Turns the query rows into the response.
    // STATUS rows: bookings [status, count], payments [status, count, sum].
    // MONTH rows: bookings [year, month, count], payments [year, month, count, sum].
    private ReportResponseDto buildReport(LocalDate start, LocalDate end, ReportGroupBy groupBy, ReportSort sort,
                                          ReportResponseDto.ListingCounts listings,
                                          List<Object[]> bookingRows, List<Object[]> paymentRows,
                                          BigDecimal income) {
        ReportResponseDto report = new ReportResponseDto();
        report.setFrom(start);
        report.setTo(end);
        report.setGroupBy(groupBy);
        report.setListings(listings);

        List<ReportResponseDto.BookingGroup> bookings = new ArrayList<>();
        for (Object[] row : bookingRows) {
            ReportResponseDto.BookingGroup group = new ReportResponseDto.BookingGroup();
            if (groupBy == ReportGroupBy.MONTH) {
                group.setGroup(monthName(row[0], row[1]));
                group.setCount(((Number) row[2]).longValue());
            } else {
                group.setGroup(row[0].toString());
                group.setCount(((Number) row[1]).longValue());
            }
            bookings.add(group);
        }
        bookings.sort(bookingOrder(sort));
        report.setBookings(bookings);

        List<ReportResponseDto.PaymentGroup> payments = new ArrayList<>();
        for (Object[] row : paymentRows) {
            ReportResponseDto.PaymentGroup group = new ReportResponseDto.PaymentGroup();
            if (groupBy == ReportGroupBy.MONTH) {
                group.setGroup(monthName(row[0], row[1]));
                group.setCount(((Number) row[2]).longValue());
                group.setTotalAmount((BigDecimal) row[3]);
            } else {
                group.setGroup(row[0].toString());
                group.setCount(((Number) row[1]).longValue());
                group.setTotalAmount((BigDecimal) row[2]);
            }
            payments.add(group);
        }
        payments.sort(paymentOrder(sort));
        report.setPayments(payments);

        // SUM gives null when there are no paid payments in the range
        report.setIncome(income == null ? BigDecimal.ZERO : income);
        return report;
    }

    // The sort is done here in Java, the queries stay the same. Groups are compared as text, which works
    // for months too because "2026-09" comes before "2026-10". Equal counts are ordered by group name.
    private Comparator<ReportResponseDto.BookingGroup> bookingOrder(ReportSort sort) {
        Comparator<ReportResponseDto.BookingGroup> byGroup = Comparator.comparing(ReportResponseDto.BookingGroup::getGroup);
        Comparator<ReportResponseDto.BookingGroup> byCount = Comparator.comparingLong(ReportResponseDto.BookingGroup::getCount);
        return switch (sort) {
            case GROUP_DESC -> byGroup.reversed();
            case COUNT_ASC -> byCount.thenComparing(byGroup);
            case COUNT_DESC -> byCount.reversed().thenComparing(byGroup);
            default -> byGroup;
        };
    }

    private Comparator<ReportResponseDto.PaymentGroup> paymentOrder(ReportSort sort) {
        Comparator<ReportResponseDto.PaymentGroup> byGroup = Comparator.comparing(ReportResponseDto.PaymentGroup::getGroup);
        Comparator<ReportResponseDto.PaymentGroup> byCount = Comparator.comparingLong(ReportResponseDto.PaymentGroup::getCount);
        return switch (sort) {
            case GROUP_DESC -> byGroup.reversed();
            case COUNT_ASC -> byCount.thenComparing(byGroup);
            case COUNT_DESC -> byCount.reversed().thenComparing(byGroup);
            default -> byGroup;
        };
    }

    // year 2026 and month 10 become "2026-10"
    private String monthName(Object year, Object month) {
        return String.format("%d-%02d", ((Number) year).intValue(), ((Number) month).intValue());
    }
}
