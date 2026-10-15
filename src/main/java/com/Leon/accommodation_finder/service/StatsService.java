package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.AdminStatsDto;
import com.Leon.accommodation_finder.dto.LandlordStatsDto;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import com.Leon.accommodation_finder.repository.PaymentRepository;
import com.Leon.accommodation_finder.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Simple counts and sums for the landlord and admin dashboards. Nothing is cached, the numbers are always current.
@Service
public class StatsService {

    private static final Logger logger = LoggerFactory.getLogger(StatsService.class);

    private final ListingRepository listingRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Autowired
    public StatsService(ListingRepository listingRepository, BookingRepository bookingRepository,
                        PaymentRepository paymentRepository, UserRepository userRepository) {
        this.listingRepository = listingRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    // landlordEmail is the logged in landlord, so a landlord can only ever see their own numbers
    public LandlordStatsDto getLandlordStats(String landlordEmail) {
        try {
            LocalDateTime thisMonthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            LocalDateTime lastMonthStart = thisMonthStart.minusMonths(1);
            LocalDateTime nextMonthStart = thisMonthStart.plusMonths(1);

            LandlordStatsDto stats = new LandlordStatsDto();
            // every listing the landlord has posted (a null status means "all statuses")
            stats.setActiveListings(listingRepository.countLandlordListings(landlordEmail, null));
            stats.setPendingRequests(
                    bookingRepository.countLandlordBookingsWithStatus(landlordEmail, BookingStatus.PENDING));
            stats.setIncomeThisMonth(orZero(
                    paymentRepository.sumLandlordIncome(landlordEmail, thisMonthStart, nextMonthStart)));
            stats.setIncomeLastMonth(orZero(
                    paymentRepository.sumLandlordIncome(landlordEmail, lastMonthStart, thisMonthStart)));
            return stats;
        } catch (Exception e) {
            logger.error("Error while calculating the landlord stats: {}", e.getMessage());
            throw e;
        }
    }

    public AdminStatsDto getAdminStats() {
        try {
            LocalDateTime thisMonthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            LocalDateTime lastMonthStart = thisMonthStart.minusMonths(1);
            LocalDateTime nextMonthStart = thisMonthStart.plusMonths(1);

            AdminStatsDto stats = new AdminStatsDto();
            stats.setTotalUsers(userRepository.count());
            stats.setTotalListings(listingRepository.count());
            stats.setNewListingsThisMonth(listingRepository.countByDateCreatedGreaterThanEqual(thisMonthStart));
            stats.setPendingBookings(bookingRepository.countByStatus(BookingStatus.PENDING));
            stats.setPaymentsReceivedThisMonth(orZero(paymentRepository.sumAllIncome(thisMonthStart, nextMonthStart)));
            stats.setPaymentsReceivedLastMonth(orZero(paymentRepository.sumAllIncome(lastMonthStart, thisMonthStart)));
            return stats;
        } catch (Exception e) {
            logger.error("Error while calculating the admin stats: {}", e.getMessage());
            throw e;
        }
    }

    // SUM gives null when there are no rows
    private BigDecimal orZero(BigDecimal amount) {
        return (amount == null) ? BigDecimal.ZERO : amount;
    }
}
