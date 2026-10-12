package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.exception.BookingNotFoundException;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.Booking;
import com.Leon.accommodation_finder.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Sends one rent due reminder for one booking. Called by RentReminderScheduler.
// It lives in its own class because @Transactional only works when the method is called from
// another class: Spring adds the transaction around the bean, a call inside the same class skips it.
@Service
public class RentReminderService {

    private static final Logger logger = LoggerFactory.getLogger(RentReminderService.class);

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RentReminderService(BookingRepository bookingRepository, ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    // Transactional: the "already reminded" date is saved and the rent.due event is sent together.
    // The event waits until the commit, so a rolled back reminder never sends an email.
    @Transactional
    public void sendReminder(Long bookingId) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(bookingId));

            // remembered so tomorrow's run does not remind about the same due date again
            booking.setLastRemindedDueDate(booking.getNextDueDate());
            bookingRepository.save(booking);

            // no student or landlord details in the event, the consumer looks them up from the booking id
            eventPublisher.publishEvent(new DomainEvent("rent.due", "SYSTEM", "SYSTEM",
                    "Booking", booking.getId(), "due date " + booking.getNextDueDate()));
        } catch (Exception e) {
            logger.error("Error while sending the rent due reminder for booking with id {}: {}", bookingId, e.getMessage());
            throw e;
        }
    }
}
