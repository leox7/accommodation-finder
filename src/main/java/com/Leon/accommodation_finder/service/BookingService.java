package com.Leon.accommodation_finder.service;

import com.Leon.accommodation_finder.dto.AdminBookingResponseDto;
import com.Leon.accommodation_finder.dto.BookingRequestDto;
import com.Leon.accommodation_finder.dto.BookingResponseDto;
import com.Leon.accommodation_finder.dto.PagedResponse;
import com.Leon.accommodation_finder.exception.BookingListingOwnershipException;
import com.Leon.accommodation_finder.exception.BookingNotCancellableException;
import com.Leon.accommodation_finder.exception.BookingNotConfirmableException;
import com.Leon.accommodation_finder.exception.BookingNotFoundException;
import com.Leon.accommodation_finder.exception.BookingNotVacatableException;
import com.Leon.accommodation_finder.exception.BookingOwnershipException;
import com.Leon.accommodation_finder.exception.InvalidBookingDatesException;
import com.Leon.accommodation_finder.exception.ListingNotAvailableException;
import com.Leon.accommodation_finder.exception.ListingNotFoundException;
import com.Leon.accommodation_finder.exception.StudentHasActiveBookingException;
import com.Leon.accommodation_finder.messaging.DomainEvent;
import com.Leon.accommodation_finder.model.Booking;
import com.Leon.accommodation_finder.model.BookingStatus;
import com.Leon.accommodation_finder.model.Listing;
import com.Leon.accommodation_finder.model.ListingStatus;
import com.Leon.accommodation_finder.model.Student;
import com.Leon.accommodation_finder.repository.BookingRepository;
import com.Leon.accommodation_finder.repository.ListingRepository;
import com.Leon.accommodation_finder.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private static final int MAX_PAGE_SIZE = 50;

    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final StudentRepository studentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public BookingService(BookingRepository bookingRepository, ListingRepository listingRepository,
                          StudentRepository studentRepository, ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.studentRepository = studentRepository;
        this.eventPublisher = eventPublisher;
    }

    // studentEmail is the currently logged in student, taken from their JWT, never from the request body
    public BookingResponseDto createBooking(BookingRequestDto request, String studentEmail) {
        try {
            LocalDate startDate = request.getStartDate();
            LocalDate endDate = request.getEndDate();

            if (startDate.isBefore(LocalDate.now())) {
                throw new InvalidBookingDatesException("Start date cannot be in the past");
            }
            if (!endDate.isAfter(startDate)) {
                throw new InvalidBookingDatesException("End date must be after the start date");
            }

            Listing listing = listingRepository.findById(request.getListingId())
                    .orElseThrow(() -> new ListingNotFoundException(request.getListingId()));

            // only a CONFIRMED stay blocks the listing, several students may have PENDING requests for the same dates
            long confirmedOverlaps = bookingRepository.countListingOverlaps(
                    listing.getId(), List.of(BookingStatus.CONFIRMED), startDate, endDate);
            if (confirmedOverlaps > 0) {
                throw new ListingNotAvailableException();
            }

            // a student can only hold one active stay at a time, on any listing
            long studentOverlaps = bookingRepository.countStudentOverlaps(
                    studentEmail, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED), startDate, endDate);
            if (studentOverlaps > 0) {
                throw new StudentHasActiveBookingException();
            }

            Student student = studentRepository.findByEmail(studentEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "No student account found for email " + studentEmail));

            Booking booking = new Booking();
            booking.setStudent(student);
            booking.setListing(listing);
            booking.setStartDate(startDate);
            booking.setEndDate(endDate);
            // status and dateCreated are system controlled, a new booking always starts as pending
            booking.setStatus(BookingStatus.PENDING);
            booking.setDateCreated(LocalDateTime.now());
            Booking saved = bookingRepository.save(booking);

            // the landlord is emailed by the notification consumer, which finds them from this booking id
            eventPublisher.publishEvent(new DomainEvent("booking.created", studentEmail, "STUDENT",
                    "Booking", saved.getId(), "Booking requested on listing " + listing.getId()));
            return toResponse(saved);
        } catch (Exception e) {
            logger.error("Error while creating booking for listing with id {}: {}", request.getListingId(), e.getMessage());
            throw e;
        }
    }

    public PagedResponse<BookingResponseDto> getMyBookings(String studentEmail, int page, int size) {
        try {
            Page<Booking> bookings = bookingRepository.findMyBookings(studentEmail, buildPageable(page, size));
            return PagedResponse.from(bookings.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching bookings for the logged in student: {}", e.getMessage());
            throw e;
        }
    }

    public BookingResponseDto cancelBooking(Long id, String studentEmail) {
        try {
            Booking booking = bookingRepository.findById(id)
                    .orElseThrow(() -> new BookingNotFoundException(id));

            if (!booking.getStudent().getEmail().equalsIgnoreCase(studentEmail)) {
                throw new BookingOwnershipException();
            }

            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new BookingNotCancellableException(booking.getStatus().name());
            }

            booking.setStatus(BookingStatus.CANCELLED);
            Booking saved = bookingRepository.save(booking);

            eventPublisher.publishEvent(new DomainEvent("booking.cancelled", studentEmail, "STUDENT",
                    "Booking", saved.getId(), "Booking cancelled by the student"));
            return toResponse(saved);
        } catch (Exception e) {
            logger.error("Error while cancelling booking with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    public PagedResponse<BookingResponseDto> getLandlordBookings(String landlordEmail, int page, int size) {
        try {
            Page<Booking> bookings = bookingRepository.findLandlordBookings(landlordEmail, buildPageable(page, size));
            return PagedResponse.from(bookings.map(this::toResponse));
        } catch (Exception e) {
            logger.error("Error while fetching bookings for the logged in landlord: {}", e.getMessage());
            throw e;
        }
    }

    // every booking in the system, for the admin list. status is optional
    public PagedResponse<AdminBookingResponseDto> getAllBookings(BookingStatus status, int page, int size) {
        try {
            Page<Booking> bookings = bookingRepository.findAllBookings(status, buildPageable(page, size));
            return PagedResponse.from(bookings.map(this::toAdminResponse));
        } catch (Exception e) {
            logger.error("Error while fetching all bookings for an admin: {}", e.getMessage());
            throw e;
        }
    }

    // Transactional because the booking, the listing and the other pending bookings change together
    @Transactional
    public BookingResponseDto confirmBooking(Long id, String landlordEmail) {
        try {
            Booking booking = bookingRepository.findById(id)
                    .orElseThrow(() -> new BookingNotFoundException(id));
            Listing listing = booking.getListing();

            if (listing.getLandlord() == null || !listing.getLandlord().getEmail().equalsIgnoreCase(landlordEmail)) {
                throw new BookingListingOwnershipException();
            }

            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new BookingNotConfirmableException(booking.getStatus().name());
            }

            // safety net: another request on this listing may have been confirmed since this one was made.
            // This booking is still PENDING here, so it is not counted itself.
            long confirmedOverlaps = bookingRepository.countListingOverlaps(
                    listing.getId(), List.of(BookingStatus.CONFIRMED), booking.getStartDate(), booking.getEndDate());
            if (confirmedOverlaps > 0) {
                throw new ListingNotAvailableException();
            }

            booking.setStatus(BookingStatus.CONFIRMED);
            listing.setStatus(ListingStatus.OCCUPIED);
            bookingRepository.save(booking);
            listingRepository.save(listing);

            // the other students asking for the same dates can no longer get them.
            // They are read first only so each one gets its own auto cancelled event.
            List<Booking> autoCancelled = bookingRepository.findOverlappingPending(
                    listing.getId(), booking.getId(), booking.getStartDate(), booking.getEndDate());
            bookingRepository.cancelOverlappingPending(
                    listing.getId(), booking.getId(), booking.getStartDate(), booking.getEndDate());

            // these events wait until the transaction commits, a rollback sends none of them
            eventPublisher.publishEvent(new DomainEvent("booking.confirmed", landlordEmail, "LANDLORD",
                    "Booking", booking.getId(), "Booking confirmed on listing " + listing.getId()));
            for (Booking other : autoCancelled) {
                eventPublisher.publishEvent(new DomainEvent("booking.auto_cancelled", "SYSTEM", "SYSTEM",
                        "Booking", other.getId(), "Cancelled because booking " + booking.getId() + " was confirmed"));
            }
            return toResponse(booking);
        } catch (Exception e) {
            logger.error("Error while confirming booking with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // Transactional because the booking and the listing change together
    @Transactional
    public BookingResponseDto vacateBooking(Long id, String studentEmail) {
        try {
            Booking booking = bookingRepository.findById(id)
                    .orElseThrow(() -> new BookingNotFoundException(id));

            if (!booking.getStudent().getEmail().equalsIgnoreCase(studentEmail)) {
                throw new BookingOwnershipException();
            }

            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new BookingNotVacatableException(booking.getStatus().name());
            }

            booking.setStatus(BookingStatus.VACATED);
            bookingRepository.save(booking);

            // a later confirmed stay (booked in advance) keeps the listing occupied
            Listing listing = booking.getListing();
            boolean otherConfirmedExists = bookingRepository.existsByListingIdAndStatusAndIdNot(
                    listing.getId(), BookingStatus.CONFIRMED, booking.getId());
            if (!otherConfirmedExists) {
                listing.setStatus(ListingStatus.AVAILABLE);
                listingRepository.save(listing);
            }

            eventPublisher.publishEvent(new DomainEvent("booking.vacated", studentEmail, "STUDENT",
                    "Booking", booking.getId(), "Student vacated listing " + listing.getId()));
            return toResponse(booking);
        } catch (Exception e) {
            logger.error("Error while vacating booking with id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    // page is at least 0, size is between 1 and 50, newest bookings first
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    // only a short listing summary and the student's first name are copied,
    // never the student's email or anything about the landlord
    private BookingResponseDto toResponse(Booking booking) {
        BookingResponseDto response = new BookingResponseDto();
        response.setId(booking.getId());
        response.setListingId(booking.getListing().getId());
        response.setListingTitle(booking.getListing().getTitle());
        response.setListingLocation(booking.getListing().getLocation());
        response.setStudentFirstName(booking.getStudent().getFirstName());
        response.setStartDate(booking.getStartDate());
        response.setEndDate(booking.getEndDate());
        response.setStatus(booking.getStatus());
        response.setDateCreated(booking.getDateCreated());
        return response;
    }

    // the same as toResponse, plus the student's last name and email so an admin can see who booked.
    // Never the student's phone number or any ID number
    private AdminBookingResponseDto toAdminResponse(Booking booking) {
        AdminBookingResponseDto response = new AdminBookingResponseDto();
        response.setId(booking.getId());
        response.setListingId(booking.getListing().getId());
        response.setListingTitle(booking.getListing().getTitle());
        response.setListingLocation(booking.getListing().getLocation());
        response.setStudentFirstName(booking.getStudent().getFirstName());
        response.setStudentLastName(booking.getStudent().getLastName());
        response.setStudentEmail(booking.getStudent().getEmail());
        response.setStartDate(booking.getStartDate());
        response.setEndDate(booking.getEndDate());
        response.setStatus(booking.getStatus());
        response.setDateCreated(booking.getDateCreated());
        return response;
    }
}
