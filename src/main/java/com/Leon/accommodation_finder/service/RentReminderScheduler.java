package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.model.Booking;
import com.Leon.accommodation_finder.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

// Once a day (reminder.cron, 08:00 Nairobi time) finds the bookings whose rent is due and unpaid,
// and asks RentReminderService to remind each landlord once per due date.
@Component
public class RentReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RentReminderScheduler.class);

    private final BookingRepository bookingRepository;
    private final RentReminderService rentReminderService;

    @Autowired
    public RentReminderScheduler(BookingRepository bookingRepository, RentReminderService rentReminderService) {
        this.bookingRepository = bookingRepository;
        this.rentReminderService = rentReminderService;
    }

    @Scheduled(cron = "${reminder.cron}", zone = "Africa/Nairobi")
    public void remindLandlords() {
        List<Booking> bookings = bookingRepository.findBookingsDueForReminder(LocalDate.now());
        logger.info("Rent due reminder job found {} booking(s) to remind", bookings.size());

        for (Booking booking : bookings) {
            // one broken booking must not stop the other reminders
            try {
                rentReminderService.sendReminder(booking.getId());
            } catch (Exception e) {
                logger.error("Rent due reminder failed for booking with id {}: {}", booking.getId(), e.getMessage());
            }
        }
    }
}
