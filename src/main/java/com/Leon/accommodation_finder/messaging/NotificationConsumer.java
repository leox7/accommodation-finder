package com.Leon.accommodation_finder.messaging;

import com.Leon.accommodation_finder.model.Booking;
import com.Leon.accommodation_finder.model.Landlord;
import com.Leon.accommodation_finder.model.Notification;
import com.Leon.accommodation_finder.model.NotificationType;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.NotificationRepository;
import com.Leon.accommodation_finder.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

// Reads booking.created and booking.confirmed from the notification queue.
// The recipient is always looked up in the database from the event's entityId,
// events never carry recipient emails or phone numbers.
@Component
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Autowired
    public NotificationConsumer(BookingRepository bookingRepository,
                                NotificationRepository notificationRepository,
                                EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    @RabbitListener(queues = "${app.rabbitmq.notification-queue}")
    public void receive(DomainEvent event) {
        // errors are logged and not rethrown: a rethrown error would make RabbitMQ
        // deliver the same message again and again
        try {
            // the booking may have been removed since the event was sent
            Optional<Booking> booking = bookingRepository.findById(event.getEntityId());
            if (booking.isEmpty()) {
                logger.warn("Booking {} from event {} no longer exists, no notification sent",
                        event.getEntityId(), event.getEventId());
                return;
            }

            if ("booking.created".equals(event.getEventType())) {
                notifyLandlordOfNewBooking(event, booking.get());
            } else if ("booking.confirmed".equals(event.getEventType())) {
                emailStudentBookingConfirmed(booking.get());
            }
        } catch (Exception e) {
            logger.error("Error while handling notification for event {}: {}", event.getEventId(), e.getMessage());
        }
    }

    private void notifyLandlordOfNewBooking(DomainEvent event, Booking booking) {
        // RabbitMQ may deliver the same message twice, the first copy is enough
        if (notificationRepository.existsByEventId(event.getEventId())) {
            logger.info("Notification for event {} already exists, skipping", event.getEventId());
            return;
        }

        Landlord landlord = booking.getListing().getLandlord();
        String message = "New booking request on your listing '" + booking.getListing().getTitle()
                + "' from " + booking.getStartDate() + " to " + booking.getEndDate() + ".";

        Notification notification = new Notification();
        notification.setLandlord(landlord);
        notification.setMessage(message);
        notification.setType(NotificationType.BOOKING);
        notification.setRead(false);
        notification.setEventId(event.getEventId());
        notification.setDateCreated(LocalDateTime.now());
        notificationRepository.save(notification);

        // the notification row is already saved, a failed email is only logged
        try {
            emailService.sendEmail(landlord.getEmail(), "New booking request", message);
        } catch (Exception e) {
            logger.error("Could not email the landlord about booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    // The ONLY place a landlord's full phone number may appear: in this email to the student,
    // so they can contact their landlord. It is read here from the database and never logged.
    private void emailStudentBookingConfirmed(Booking booking) {
        Landlord landlord = booking.getListing().getLandlord();
        String message = "Your booking for '" + booking.getListing().getTitle() + "' from "
                + booking.getStartDate() + " to " + booking.getEndDate() + " has been confirmed.\n"
                + "You can contact your landlord, " + landlord.getFirstName() + ", on " + landlord.getPhoneNumber() + ".";

        try {
            emailService.sendEmail(booking.getStudent().getEmail(), "Your booking is confirmed", message);
        } catch (Exception e) {
            logger.error("Could not email the student about confirmed booking {}: {}", booking.getId(), e.getMessage());
        }
    }
}
