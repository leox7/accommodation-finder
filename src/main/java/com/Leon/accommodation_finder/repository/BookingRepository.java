package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Booking;
import com.Leon.accommodation_finder.model.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Two stays overlap when existingStart <= newEnd AND existingEnd >= newStart.
    // Only bookings whose status is in the given list are counted.
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.listing.id = :listingId " +
            "AND b.status IN :statuses " +
            "AND b.startDate <= :endDate AND b.endDate >= :startDate")
    long countListingOverlaps(@Param("listingId") Long listingId,
                              @Param("statuses") List<BookingStatus> statuses,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    // same overlap rule, but for one student's bookings on any listing
    @Query("SELECT COUNT(b) FROM Booking b WHERE LOWER(b.student.email) = LOWER(:email) " +
            "AND b.status IN :statuses " +
            "AND b.startDate <= :endDate AND b.endDate >= :startDate")
    long countStudentOverlaps(@Param("email") String email,
                              @Param("statuses") List<BookingStatus> statuses,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);

    // used before deleting a listing, true if any booking on it is in one of the given statuses
    boolean existsByListingIdAndStatusIn(Long listingId, List<BookingStatus> statuses);

    // removes the remaining (cancelled or vacated) bookings when their listing is deleted
    void deleteByListingId(Long listingId);

    @Query("SELECT b FROM Booking b WHERE LOWER(b.student.email) = LOWER(:email)")
    Page<Booking> findMyBookings(@Param("email") String email, Pageable pageable);

    // every booking, for admins. A null status means "all statuses"
    @Query("SELECT b FROM Booking b WHERE (:status IS NULL OR b.status = :status)")
    Page<Booking> findAllBookings(@Param("status") BookingStatus status, Pageable pageable);

    // ---- profile and dashboard counts, for one status ----

    @Query("SELECT COUNT(b) FROM Booking b WHERE LOWER(b.student.email) = LOWER(:email) AND b.status = :status")
    long countStudentBookingsWithStatus(@Param("email") String email, @Param("status") BookingStatus status);

    @Query("SELECT COUNT(b) FROM Booking b WHERE LOWER(b.listing.landlord.email) = LOWER(:email) AND b.status = :status")
    long countLandlordBookingsWithStatus(@Param("email") String email, @Param("status") BookingStatus status);

    long countByStatus(BookingStatus status);

    // bookings on every listing owned by the given landlord
    @Query("SELECT b FROM Booking b WHERE LOWER(b.listing.landlord.email) = LOWER(:email)")
    Page<Booking> findLandlordBookings(@Param("email") String email, Pageable pageable);

    // the same bookings that cancelOverlappingPending will cancel, read first so each one gets an event
    @Query("SELECT b FROM Booking b WHERE b.listing.id = :listingId " +
            "AND b.status = com.Leon.accommodation_finder.model.BookingStatus.PENDING " +
            "AND b.id <> :bookingId " +
            "AND b.startDate <= :endDate AND b.endDate >= :startDate")
    List<Booking> findOverlappingPending(@Param("listingId") Long listingId,
                                         @Param("bookingId") Long bookingId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    // used when a booking is confirmed: every other PENDING request on the same listing
    // whose dates overlap the confirmed stay is cancelled in one statement.
    // Must be called inside a transaction (the calling service method is @Transactional).
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.Leon.accommodation_finder.model.BookingStatus.CANCELLED " +
            "WHERE b.listing.id = :listingId " +
            "AND b.status = com.Leon.accommodation_finder.model.BookingStatus.PENDING " +
            "AND b.id <> :bookingId " +
            "AND b.startDate <= :endDate AND b.endDate >= :startDate")
    int cancelOverlappingPending(@Param("listingId") Long listingId,
                                 @Param("bookingId") Long bookingId,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    // used after vacating: true if the listing still has another booking with the given status
    boolean existsByListingIdAndStatusAndIdNot(Long listingId, BookingStatus status, Long id);

    // Confirmed bookings whose rent is due today or earlier and still unpaid (paying moves nextDueDate forward),
    // that are still inside the stay, and whose current due date has not been reminded about yet
    @Query("SELECT b FROM Booking b " +
            "WHERE b.status = com.Leon.accommodation_finder.model.BookingStatus.CONFIRMED " +
            "AND b.nextDueDate IS NOT NULL " +
            "AND b.nextDueDate <= :today " +
            "AND b.nextDueDate <= b.endDate " +
            "AND (b.lastRemindedDueDate IS NULL OR b.lastRemindedDueDate < b.nextDueDate)")
    List<Booking> findBookingsDueForReminder(@Param("today") LocalDate today);

    // ---- report queries: bookings created from :from (included) to :to (not included).
    // A null status means "all statuses". A STATUS row is [status, count], a MONTH row is [year, month, count].
    // The landlord versions only count bookings on that landlord's own listings. ----

    @Query("SELECT b.status, COUNT(b) FROM Booking b WHERE b.listing.landlord.email = :email " +
            "AND b.dateCreated >= :from AND b.dateCreated < :to " +
            "AND (:status IS NULL OR b.status = :status) " +
            "GROUP BY b.status ORDER BY b.status")
    List<Object[]> countLandlordBookingsByStatus(@Param("email") String email,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 @Param("status") BookingStatus status);

    @Query("SELECT YEAR(b.dateCreated), MONTH(b.dateCreated), COUNT(b) FROM Booking b " +
            "WHERE b.listing.landlord.email = :email " +
            "AND b.dateCreated >= :from AND b.dateCreated < :to " +
            "AND (:status IS NULL OR b.status = :status) " +
            "GROUP BY YEAR(b.dateCreated), MONTH(b.dateCreated) " +
            "ORDER BY YEAR(b.dateCreated), MONTH(b.dateCreated)")
    List<Object[]> countLandlordBookingsByMonth(@Param("email") String email,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                @Param("status") BookingStatus status);

    @Query("SELECT b.status, COUNT(b) FROM Booking b " +
            "WHERE b.dateCreated >= :from AND b.dateCreated < :to " +
            "AND (:status IS NULL OR b.status = :status) " +
            "GROUP BY b.status ORDER BY b.status")
    List<Object[]> countAllBookingsByStatus(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("status") BookingStatus status);

    @Query("SELECT YEAR(b.dateCreated), MONTH(b.dateCreated), COUNT(b) FROM Booking b " +
            "WHERE b.dateCreated >= :from AND b.dateCreated < :to " +
            "AND (:status IS NULL OR b.status = :status) " +
            "GROUP BY YEAR(b.dateCreated), MONTH(b.dateCreated) " +
            "ORDER BY YEAR(b.dateCreated), MONTH(b.dateCreated)")
    List<Object[]> countAllBookingsByMonth(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to,
                                           @Param("status") BookingStatus status);
}
